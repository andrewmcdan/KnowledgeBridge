FROM oven/bun:1.3.13

# Upstream stays external; update this revision deliberately and smoke-test it.
ARG GBRAIN_REVISION=a6be012a3bcfac42e279630aedec5cda4a450e29
ADD https://github.com/garrytan/gbrain/archive/${GBRAIN_REVISION}.tar.gz /tmp/gbrain.tar.gz
WORKDIR /opt/gbrain
RUN tar -xzf /tmp/gbrain.tar.gz --strip-components=1 -C /opt/gbrain \
    && rm /tmp/gbrain.tar.gz \
    && bun install --frozen-lockfile

COPY entrypoint.sh /usr/local/bin/gbrain-entrypoint
RUN chmod +x /usr/local/bin/gbrain-entrypoint
EXPOSE 3131
ENTRYPOINT ["/usr/local/bin/gbrain-entrypoint"]
