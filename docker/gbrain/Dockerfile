FROM oven/bun:1.4.2

# Upstream stays external; update this revision deliberately and smoke-test it.
ARG GBRAIN_REVISION=109b992172e1f49107f9de9841758c1d043a2668
ADD https://github.com/garrytan/gbrain/archive/${GBRAIN_REVISION}.tar.gz /tmp/gbrain.tar.gz
WORKDIR /opt/gbrain
RUN tar -xzf /tmp/gbrain.tar.gz --strip-components=1 -C /opt/gbrain \
    && rm /tmp/gbrain.tar.gz \
    && bun install --frozen-lockfile

COPY entrypoint.sh /usr/local/bin/gbrain-entrypoint
RUN chmod +x /usr/local/bin/gbrain-entrypoint
EXPOSE 3131
ENTRYPOINT ["/usr/local/bin/gbrain-entrypoint"]
