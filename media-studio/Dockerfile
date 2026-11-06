FROM node:22-bookworm-slim

ENV NODE_ENV=production HOST=0.0.0.0 PORT=8787 DATA_FILE=/data/jobs.json
RUN apt-get update \
 && apt-get install -y --no-install-recommends ffmpeg fonts-noto-core ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /app /data \
 && chown -R node:node /app /data

WORKDIR /app
COPY --chown=node:node package.json ./package.json
COPY --chown=node:node server.mjs ./server.mjs
COPY --chown=node:node index.html ./index.html
COPY --chown=node:node src ./src
COPY --chown=node:node scripts ./scripts

USER node
EXPOSE 8787
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD node -e "const p=process.env.PORT||8787;fetch('http://127.0.0.1:'+p+'/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "server.mjs"]
