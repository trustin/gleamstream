package com.limelight.binding.video;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;

import javax.swing.JFrame;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.cpu.AvcDecoder;

public class SwingCpuDecoderRenderer implements VideoDecoderRenderer {

	private Thread rendererThread;
	private int targetFps;
	private int width, height;

	private Graphics graphics;
	private BufferedImage image;
	
	private static final int DECODER_BUFFER_SIZE = 92*1024;
	private ByteBuffer decoderBuffer;
	
	// Only sleep if the difference is above this value
	private static final int WAIT_CEILING_MS = 8;
	
	@Override
	public void setup(int width, int height, Object renderTarget, int drFlags) {
		this.targetFps = 30;
		this.width = width;
		this.height = height;
		
		// Single threaded low latency decode is ideal
		int avcFlags = AvcDecoder.LOW_LATENCY_DECODE;
		int threadCount = 1;
		
		// Hack to work around the bad Java native library loader
		// which can't resolve native library dependencies
		if (System.getProperty("os.name").contains("Windows")) {
			System.loadLibrary("avutil-52");
			System.loadLibrary("postproc-52");
			System.loadLibrary("pthreadVC2");
		}
		
		int err = AvcDecoder.init(width, height, avcFlags, threadCount);
		if (err != 0) {
			throw new IllegalStateException("AVC decoder initialization failure: "+err);
		}
		
		graphics = ((JFrame)renderTarget).getGraphics();

		image = new BufferedImage(width, height,
	            BufferedImage.TYPE_INT_BGR);
		
		decoderBuffer = ByteBuffer.allocate(DECODER_BUFFER_SIZE + AvcDecoder.getInputPaddingSize());
		
		System.out.println("Using software decoding");
	}

	@Override
	public void start() {
		rendererThread = new Thread() {
			@Override
			public void run() {
				long nextFrameTime = System.currentTimeMillis();
				int[] imageBuffer = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
				
				while (!isInterrupted())
				{
					long diff = nextFrameTime - System.currentTimeMillis();

					if (diff < WAIT_CEILING_MS) {
						// We must call Thread.sleep in order to be interruptable
						diff = 0;
					}
					
					try {
						Thread.sleep(diff);
					} catch (InterruptedException e) {
						return;
					}
					
					nextFrameTime = computePresentationTimeMs(targetFps);
					if (AvcDecoder.getRgbFrameInt(imageBuffer, imageBuffer.length)) {
						graphics.drawImage(image, 0, 0, width, height, null);
					}
				}
			}
		};
		rendererThread.setName("Video - Renderer (CPU)");
		rendererThread.start();
	}
	
	private long computePresentationTimeMs(int frameRate) {
		return System.currentTimeMillis() + (1000 / frameRate);
	}

	@Override
	public void stop() {
		rendererThread.interrupt();
		
		try {
			rendererThread.join();
		} catch (InterruptedException e) { }
	}

	@Override
	public void release() {
		AvcDecoder.destroy();
	}

	@Override
	public boolean submitDecodeUnit(DecodeUnit decodeUnit) {
		byte[] data;
		
		// Use the reserved decoder buffer if this decode unit will fit
		if (decodeUnit.getDataLength() <= DECODER_BUFFER_SIZE) {
			decoderBuffer.clear();
			
			for (ByteBufferDescriptor bbd : decodeUnit.getBufferList()) {
				decoderBuffer.put(bbd.data, bbd.offset, bbd.length);
			}
			
			data = decoderBuffer.array();
		}
		else {
			data = new byte[decodeUnit.getDataLength()+AvcDecoder.getInputPaddingSize()];
			
			int offset = 0;
			for (ByteBufferDescriptor bbd : decodeUnit.getBufferList()) {
				System.arraycopy(bbd.data, bbd.offset, data, offset, bbd.length);
				offset += bbd.length;
			}
		}
		
		return (AvcDecoder.decode(data, 0, decodeUnit.getDataLength()) == 0);
	}
}
