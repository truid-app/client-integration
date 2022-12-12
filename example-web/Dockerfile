FROM node:16.18.1-buster

WORKDIR /build
COPY . .
RUN yarn build


FROM nginx:1.23.2-alpine

EXPOSE 8080
COPY --from=0 /build/dist/* /www/
COPY --from=0 /build/src/conf/nginx.conf /etc/nginx/conf.d/example-web.conf
