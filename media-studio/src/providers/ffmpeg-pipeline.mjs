import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { mkdir, stat, chmod } from "node:fs/promises";
import { dirname, extname, resolve, sep } from "node:path";

const run = promisify(execFile);
const ROOT = resolve(process.env.DATA_FILE ? dirname(resolve(process.env.DATA_FILE)) : "./data", "media");
const MAX_SOURCE_BYTES = 1024 * 1024 * 1024;
const MIME_EXT = Object.freeze({
  "video/mp4": ".mp4",
  "video/webm": ".webm",
  "video/quicktime": ".mov"
});

function mediaPath(id, suffix) {
  if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error("invalid_media_id");
  const path = resolve(ROOT, id + suffix);
  if (!path.startsWith(ROOT + sep)) throw new Error("invalid_media_path");
  return path;
}

export function extensionForMime(mime) {
  return MIME_EXT[String(mime || "").split(";")[0].trim().toLowerCase()] || null;
}

async function commandAvailable(command) {
  try { await run(command, ["-version"], { timeout: 4000, windowsHide: true }); return true; }
  catch (error) { return error.code !== "ENOENT" && error.code !== "EACCES"; }
}

export async function getMediaEngineStatus() {
  const [ffmpeg, ffprobe] = await Promise.all([commandAvailable("ffmpeg"), commandAvailable("ffprobe")]);
  return {
    id: "media_processing",
    name: "FFmpeg auto-edit and thumbnail",
    configured: ffmpeg && ffprobe,
    status: ffmpeg && ffprobe ? "ready_local_binary" : "not_installed",
    ffmpeg,
    ffprobe,
    note: ffmpeg && ffprobe ? "Local processing is available; a source video is still required." : "Install FFmpeg and ffprobe on the server to enable media processing."
  };
}

export async function inspectVideo(filePath) {
  const safePath = resolve(filePath);
  if (!safePath.startsWith(ROOT + sep)) throw new Error("media_path_outside_storage");
  const info = await stat(safePath);
  if (!info.isFile() || info.size <= 0 || info.size > MAX_SOURCE_BYTES) throw new Error("invalid_media_size");
  const { stdout } = await run("ffprobe", [
    "-v", "error", "-show_entries", "format=duration,format_name:stream=codec_type,width,height",
    "-of", "json", safePath
  ], { timeout: 15000, maxBuffer: 1024 * 1024, windowsHide: true });
  const parsed = JSON.parse(stdout);
  const duration = Number(parsed?.format?.duration);
  const streams = Array.isArray(parsed?.streams) ? parsed.streams : [];
  const video = streams.find(x => x.codec_type === "video");
  if (!video || !Number.isFinite(duration) || duration <= 0 || duration > 1800) throw new Error("unsupported_video_or_duration");
  return {
    bytes: info.size,
    durationSeconds: Math.round(duration * 100) / 100,
    width: Number(video.width) || null,
    height: Number(video.height) || null,
    hasAudio: streams.some(x => x.codec_type === "audio"),
    format: String(parsed?.format?.format_name || "")
  };
}

export async function autoEditVideo(sourcePath, jobId) {
  const input = resolve(sourcePath);
  if (!input.startsWith(ROOT + sep)) throw new Error("media_path_outside_storage");
  const output = mediaPath(jobId, "-edited.mp4");
  const thumbnail = mediaPath(jobId, "-thumbnail.jpg");
  await mkdir(ROOT, { recursive: true, mode: 0o700 });
  await chmod(ROOT, 0o700);
  const probe = await inspectVideo(input);
  const args = [
    "-y", "-i", input,
    "-map", "0:v:0", "-map", "0:a?",
    "-vf", "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30",
    "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
    ...(probe.hasAudio ? ["-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "aac", "-b:a", "192k"] : ["-an"]),
    "-movflags", "+faststart", output
  ];
  await run("ffmpeg", args, { timeout: 20 * 60 * 1000, maxBuffer: 4 * 1024 * 1024, windowsHide: true });
  await run("ffmpeg", [
    "-y", "-ss", String(Math.min(3, Math.max(0, probe.durationSeconds / 3))),
    "-i", output, "-frames:v", "1",
    "-vf", "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2",
    "-q:v", "3", thumbnail
  ], { timeout: 60000, maxBuffer: 1024 * 1024, windowsHide: true });
  await Promise.all([chmod(output, 0o600), chmod(thumbnail, 0o600)]);
  const [editedInfo, thumbnailInfo] = await Promise.all([stat(output), stat(thumbnail)]);
  return {
    outputPath: output,
    thumbnailPath: thumbnail,
    editedBytes: editedInfo.size,
    thumbnailBytes: thumbnailInfo.size,
    source: probe,
    profile: "1080p-30fps-h264-aac-loudness-normalized"
  };
}


export async function qualityCheckVideo(filePath) {
  const media = await inspectVideo(filePath);
  const checks = {
    readableVideo: media.durationSeconds > 0 && media.width > 0 && media.height > 0,
    hdResolution: media.width >= 1280 && media.height >= 720,
    audioTrack: media.hasAudio,
    durationWithinLimit: media.durationSeconds <= 900
  };
  return {
    passed: Object.values(checks).every(Boolean),
    checks,
    media: {
      durationSeconds: media.durationSeconds,
      width: media.width,
      height: media.height,
      hasAudio: media.hasAudio,
      bytes: media.bytes
    },
    checkedAt: new Date().toISOString()
  };
}

export const MEDIA_LIMITS = Object.freeze({ maxSourceBytes: MAX_SOURCE_BYTES, maxDurationSeconds: 1800 });
