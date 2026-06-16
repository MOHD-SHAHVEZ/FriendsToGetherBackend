FROM ubuntu:latest
LABEL authors="mohds"

ENTRYPOINT ["top", "-b"]