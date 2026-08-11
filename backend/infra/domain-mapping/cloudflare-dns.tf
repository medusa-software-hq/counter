# The API's public DNS record is intentionally absent: it pointed at the removed
# compute platform's domain mapping. The cloudflare provider (see main.tf) is
# kept so the AWS-targeting record can be added here later, at which point its
# zone id and host name are reintroduced alongside the record that consumes them.
