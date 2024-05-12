FROM gradle:8.2-jdk17 AS builder

COPY . /app
WORKDIR /app/
RUN gradle CampfireServer:build CampfireServerMedia:build --no-daemon

FROM rust:1.77-buster AS rust-builder

WORKDIR /app
COPY ./rust-auth /app/rust-auth
COPY ./rust-core /app/rust-core
COPY ./rust-email /app/rust-email
COPY ./rust-level /app/rust-level
COPY ./rust-notification /app/rust-notification
COPY ./rust-melior /app/rust-melior
COPY ./rust-profile /app/rust-profile
COPY ./.sqlx /app/.sqlx
COPY ./migrations /app/migrations
COPY ./Cargo.toml ./Cargo.lock /app/
RUN cargo build --release

FROM azul/zulu-openjdk-debian:17-jre AS runner

WORKDIR /app/
RUN mkdir -p /app/CampfireServer/res /app/lib

RUN apt-get update && apt-get install -y tar openssl bash findutils curl
RUN curl -o /app/bcprov.jar https://downloads.bouncycastle.org/java/bcprov-jdk15on-170.jar

COPY --from=builder /app/docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

COPY --from=builder /app/CampfireServer/build/distributions/CampfireServer.tar /app/
COPY --from=builder /app/CampfireServerMedia/build/distributions/CampfireServerMedia.tar /app/
RUN tar -xf CampfireServer.tar && rm CampfireServer.tar
RUN tar -xf CampfireServerMedia.tar && rm CampfireServerMedia.tar

COPY --from=builder /app/CampfireServer/res /app/CampfireServer/res

COPY --from=rust-builder /app/target/release/b-melior /app/rust-bonfire

EXPOSE 4022 4023 4024 4026 4027 4028 4051
VOLUME /app/secrets

CMD "/app/docker-entrypoint.sh"
