import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

# 실행 시 인자로 포트 번호를 받거나, 없으면 8080 사용
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080

class SimpleBackendHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        print(f"\n[Backend-{PORT}] Received Request from: {self.client_address}")

        self.send_response(200)
        self.send_header('Content-type', 'text/plain; charset=utf-8')
        self.end_headers()

        # 응답 메시지에 포트 번호를 포함시켜서 누가 응답했는지 알 수 있게 함
        response_message = f"Hello from Backend Server running on Port {PORT}!"
        self.wfile.write(response_message.encode('utf-8'))

def run():
    server_address = ('', PORT)
    httpd = HTTPServer(server_address, SimpleBackendHandler)
    print(f">>> Backend Server started on port {PORT}...")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()

if __name__ == '__main__':
    run()