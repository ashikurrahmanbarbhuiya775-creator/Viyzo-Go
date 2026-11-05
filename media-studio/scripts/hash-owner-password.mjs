import { readFile } from "node:fs/promises";
import { hashOwnerPassword } from "../src/security/owner-auth.mjs";

const input = await readFile(0, "utf8");
const password = input.replace(/\r?\n$/, "");
try {
  process.stdout.write(hashOwnerPassword(password) + "\n");
} catch (error) {
  process.stderr.write(error.message + "\n");
  process.exitCode = 1;
}
