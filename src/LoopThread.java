
public abstract class LoopThread extends Thread {
	@Override
	final public void run() {
		onStart();
		while (!isInterrupted()) {
			try {
				loop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		onStop();
	}

	protected abstract void loop() throws Exception;

	protected void onStart() {
	}

	protected void onStop() {
	}
}
