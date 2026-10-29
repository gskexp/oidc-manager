Place your organisation's root/intermediate CA certificates here as PEM `.pem`, `.crt`, or `.cer` files.

The application adds these certificates to the JVM's normal trusted CAs when creating an HTTP client. Restart the operation after adding a file. Certificate bundles containing multiple PEM certificates are supported.

Use the CA that issued the remote token server's certificate, including a corporate proxy CA when applicable. The localhost certificate in `data/callback-tls/` is for the browser callback listener and does not establish trust of the remote token server.
