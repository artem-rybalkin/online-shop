FROM nginx:alpine
COPY ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY src/main/resources/static/index.html /usr/share/nginx/html/
COPY src/main/resources/static/style.css /usr/share/nginx/html/
COPY src/main/resources/static/js/ /usr/share/nginx/html/js/
