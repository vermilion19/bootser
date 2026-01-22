from http.server import BaseHTTPRequestHandler, HTTPServer

class SimpleBackendHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        # 1. 요청 로그 출력
        print(f"\n[Backend] Received Request from: {self.client_address}")
        print(f"[Backend] Path: {self.path}")
        print("[Backend] Headers:")
        print(self.headers)

        # 2. 응답 헤더 설정
        self.send_response(200)
        self.send_header('Content-type', 'text/plain; charset=utf-8')
        self.end_headers()

        # 3. 응답 바디 전송
        response_message = "Hello from Python Backend! (Via Netty Proxy)"
        self.wfile.write(response_message.encode('utf-8'))

def run(server_class=HTTPServer, handler_class=SimpleBackendHandler, port=8080):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    print(f">>> Python Backend Server running on port {port}...")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    print(">>> Server stopped.")

if __name__ == '__main__':
    run()