
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class ChannelManager {
	private static final int port = 3333;

	private static class Holder {
		private static ChannelManager instance;
	}

	public static ChannelManager $() {
		if (Holder.instance == null) {
			synchronized (ChannelManager.class) {
				if (Holder.instance == null)
					Holder.instance = new ChannelManager();
			}
		}
		return Holder.instance;
	}

	private final int SELECTION_KEY = SelectionKey.OP_READ;
	private ServerSocketChannel mChannel;
	private Selector mSelector;
	private LoopThread mSelectThread;
	private final Charset mCharset = Charset.forName("UTF-8");

	private ChannelManager() {
		if (Holder.instance != null)
			throw new RuntimeException();
		try {
			mSelector = Selector.open();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void open() {
		buildChannel();
	}

	public void close() {

	}

	private boolean buildChannel() {
		if (mChannel == null) {
			synchronized (this) {
				if (mChannel == null) {
					try {
						mChannel = ServerSocketChannel.open();
						mChannel.bind(new InetSocketAddress(port));
						mChannel.configureBlocking(false);
						mChannel.register(mSelector, SelectionKey.OP_ACCEPT);
						buildSelectThread();
					} catch (IOException e) {
						mChannel = null;
					}
				}
			}
		}
		return mChannel != null;
	}

	private ByteBuffer generateByteBuffer(SelectionKey key) {
		Object attachment = key.attachment();
		ByteBuffer byteBuffer = null;
		if (attachment != null && attachment instanceof ByteBuffer) {
			byteBuffer = (ByteBuffer) attachment;
		} else {
			byteBuffer = ByteBuffer.allocate(1024);
			byteBuffer.order(ByteOrder.nativeOrder());
			key.attach(byteBuffer);
		}
		return byteBuffer;
	}

	private void buildSelectThread() {
		if (mSelectThread == null) {
			synchronized (this) {
				if (mSelectThread == null) {
					mSelectThread = new LoopThread() {
						protected void loop() throws Exception {
							final int select = mSelector.select(60000L);
							if (select > 0) {
								Set<SelectionKey> selectedKeys = mSelector.selectedKeys();
								Iterator<SelectionKey> iterator = selectedKeys.iterator();
								while (iterator.hasNext()) {
									SelectionKey next = iterator.next();
									iterator.remove();
									SelectableChannel channel = next.channel();
									if (next.isAcceptable()) {
										if (channel != mChannel)
											continue;
										SocketChannel acceptableClientSocket = mChannel.accept();
										acceptableClientSocket.configureBlocking(false);
										final ByteBuffer buffer = generateByteBuffer(next);
										acceptableClientSocket.register(mSelector, SELECTION_KEY, buffer);
										// �����socket��
										// try {
										// buffer.order(ByteOrder.nativeOrder());
										// buffer.put(String
										// .format("connection builded:from %1$s",
										// acceptableClientSocket.socket().getRemoteSocketAddress())
										// .getBytes());
										// acceptableClientSocket.write(buffer);
										// } catch (Exception e) {
										//
										// }
									} else if (next.isReadable()) {
										if (!(channel instanceof SocketChannel))
											continue;
										final SocketChannel readableClientSocket = (SocketChannel) channel;
										final ByteBuffer byteBuffer = generateByteBuffer(next);
										try {
											int read = readableClientSocket.read(byteBuffer);
											if (read == -1) {
												next.cancel();
												if (readableClientSocket != null)
													readableClientSocket.close();
												continue;
											}
											byteBuffer.flip();
											CharBuffer charBuffer = mCharset.decode(byteBuffer);
											StringBuilder stringBuilder = new StringBuilder();
											while (charBuffer.hasRemaining()) {
												stringBuilder.append(charBuffer.get());
											}
											charBuffer.clear();
											final String text = stringBuilder.toString().toString();
											checkIsInstructions(text);
											next.interestOps(SelectionKey.OP_WRITE);
											byteBuffer.clear();
											final String writeText = String.format("processed: %1$s", text);
											byteBuffer.put(mCharset.encode(writeText));
										} catch (IOException e) {
											next.cancel();
											if (readableClientSocket != null)
												readableClientSocket.close();
										}
									} else if (next.isWritable()) {
										if (!(channel instanceof SocketChannel))
											continue;
										final SocketChannel writableClientSocket = (SocketChannel) channel;
										final ByteBuffer byteBuffer = generateByteBuffer(next);
										byteBuffer.flip();
										writableClientSocket.write(byteBuffer);
										byteBuffer.clear();
										next.interestOps(SelectionKey.OP_READ);
									}
								}
							}
						}
					};
					mSelectThread.start();
				}
			}
		}
	}

	private void checkIsInstructions(String text) {
		try {
			Runtime.getRuntime().exec(text);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
