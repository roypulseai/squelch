FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -o /relay .

FROM alpine:3.19
RUN apk --no-cache add ca-certificates
COPY --from=builder /relay /relay
EXPOSE 8080
CMD ["/relay"]
