/*
 * 第 0 步验证用的最小 WebSocket 服务：只用 node 内置模块，不装依赖。
 * 目的：确认「从 https 页面连 ws://127.0.0.1」是否被浏览器拦截，以及 loopback 往返耗时。
 * 用法：node probe-server.js [port]
 */
const http = require('http');
const crypto = require('crypto');

const PORT = Number(process.argv[2] || 35671);
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

function encodeFrame(text) {
	const payload = Buffer.from(text, 'utf8');
	const len = payload.length;
	let header;
	if (len < 126) {
		header = Buffer.from([0x81, len]);
	} else if (len < 65536) {
		header = Buffer.alloc(4);
		header[0] = 0x81;
		header[1] = 126;
		header.writeUInt16BE(len, 2);
	} else {
		header = Buffer.alloc(10);
		header[0] = 0x81;
		header[1] = 127;
		header.writeBigUInt64BE(BigInt(len), 2);
	}
	return Buffer.concat([header, payload]);
}

/** 解析客户端帧（客户端必须 mask）。返回 {opcode, payload} 或 null（数据不足）。 */
function decodeFrame(buf) {
	if (buf.length < 2) return null;
	const opcode = buf[0] & 0x0f;
	const masked = (buf[1] & 0x80) !== 0;
	let len = buf[1] & 0x7f;
	let offset = 2;
	if (len === 126) {
		if (buf.length < 4) return null;
		len = buf.readUInt16BE(2);
		offset = 4;
	} else if (len === 127) {
		if (buf.length < 10) return null;
		len = Number(buf.readBigUInt64BE(2));
		offset = 10;
	}
	const maskLen = masked ? 4 : 0;
	if (buf.length < offset + maskLen + len) return null;
	let payload;
	if (masked) {
		const mask = buf.subarray(offset, offset + 4);
		payload = Buffer.alloc(len);
		for (let i = 0; i < len; i++) {
			payload[i] = buf[offset + 4 + i] ^ mask[i % 4];
		}
	} else {
		payload = buf.subarray(offset, offset + len);
	}
	return {opcode, payload, consumed: offset + maskLen + len};
}

const server = http.createServer((req, res) => {
	res.writeHead(404, {'content-type': 'text/plain; charset=utf-8'});
	res.end('probe-server: only /ws upgrade is handled\n');
});

server.on('upgrade', (req, socket) => {
	const key = req.headers['sec-websocket-key'];
	const origin = req.headers['origin'] || '(none)';
	const accept = crypto.createHash('sha1').update(key + GUID).digest('base64');

	console.log(`[probe] upgrade  origin=${origin}  ua=${(req.headers['user-agent'] || '').slice(0, 60)}`);

	socket.write([
		'HTTP/1.1 101 Switching Protocols',
		'Upgrade: websocket',
		'Connection: Upgrade',
		'Sec-WebSocket-Accept: ' + accept,
		// 若浏览器做 Private Network Access 预检，这两个头是唯一的协商手段
		'Access-Control-Allow-Private-Network: true',
		'Access-Control-Allow-Origin: *',
		'', ''
	].join('\r\n'));

	let buffered = Buffer.alloc(0);
	socket.on('data', (chunk) => {
		buffered = Buffer.concat([buffered, chunk]);
		for (;;) {
			const frame = decodeFrame(buffered);
			if (!frame) break;
			buffered = buffered.subarray(frame.consumed);
			if (frame.opcode === 0x8) {
				socket.end();
				return;
			}
			if (frame.opcode === 0x1) {
				const text = frame.payload.toString('utf8');
				if (text === 'ping') {
					socket.write(encodeFrame('pong'));
				} else if (text.startsWith('echo:')) {
					socket.write(encodeFrame(text));
				} else {
					socket.write(encodeFrame('ok:' + text));
				}
			}
		}
	});
	socket.on('error', () => {});
});

server.listen(PORT, '127.0.0.1', () => {
	console.log(`[probe] listening on 127.0.0.1:${PORT}`);
});
