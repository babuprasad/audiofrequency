package edu.buffalo.audiofrequency;

import edu.buffalo.realdoublefft.RealDoubleFFT;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity {
	private static final String TAG = "AudioRecord";
	Button startStop = null;
	Button reset = null;
	TextView freqText = null;
	boolean recordStart = false;

	ImageView signalFrequency;
	Bitmap bitmap;
	Canvas canvas;
	Paint paint;
	AudioThread audioThread = null;
	AudioRecord localAudioRecord = null;
	int max2 = 0, min2 = 32768;
	WakeLock screenLock = null;
	PowerManager powerManager = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		startStop = (Button) findViewById(R.id.buttonStart);
		reset = (Button) findViewById(R.id.buttonReset);
		freqText = (TextView) findViewById(R.id.maxFreqText);

		signalFrequency = (ImageView) this.findViewById(R.id.signalFrequency);
		powerManager = (PowerManager) MainActivity.this.getSystemService(POWER_SERVICE);
		screenLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "timerWakeLock");
		bitmap = Bitmap.createBitmap((int) 256, (int) 100,
				Bitmap.Config.ARGB_8888);
		canvas = new Canvas(bitmap);
		paint = new Paint();
		paint.setColor(Color.GREEN);
		signalFrequency.setImageBitmap(bitmap);
		startStop.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if(!recordStart) 
				{
					audioThread = new AudioThread();
					audioThread.start();
					startStop.setText("Stop");
					//freqText.setText("Recording started...\n");
					recordStart = true;
					screenLock.acquire();
				}
				else
				{
					audioThread.close(localAudioRecord);
					startStop.setText("Start");
					//freqText.setText("Recording completed.\n");
					recordStart = false;	
					max2 =0;
					signalFrequency.invalidate();
					screenLock.release();
				}
			}
		});

		reset.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				//freqText.setText("Freq1: \n Max1 :\n");
				freqText.setText("Freq2 : \n Max2 : \n");
			}
		});
		//AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	class AudioThread extends Thread
	{
		boolean stopped = false;
		AudioRecord audioRecord = null;
		int blockSize = 256;
		int ix       = 0;
		int sampleRate = 8000;
		short[] buffer = new short[blockSize];
		double[] transformBuffer = new double[blockSize];
		RealDoubleFFT transformer = null;

		@Override
		public void run() {
			transformer = new RealDoubleFFT(blockSize);
			//android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
			try {
				int N = AudioRecord.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
				audioRecord = new AudioRecord(AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,N*10);
				audioRecord.startRecording();
				localAudioRecord = audioRecord;
				int max = 0;
				while(!stopped) { 
					int bufferReadResult = audioRecord.read(buffer,0,buffer.length);
					//int frequency = calculate(sampleRate, buffer);
					//max = (frequency > max)?frequency:max;
					//displayMsg("Freq1 : "+frequency + "\n Max1 : " + max);
					for (int i = 0; i < blockSize && i < bufferReadResult; i++) {
						transformBuffer[i] = (double) buffer[i] / 32768.0; 
					}
					transformer.ft(transformBuffer);
					drawImage(transformBuffer);
					
				}
			} catch(Throwable x) { 
				Log.w(TAG,"Error reading voice audio",x);
			} finally { 
				close(audioRecord);
			}
		}

		public void close(AudioRecord audioRecord)
		{
			audioRecord.stop();
			stopped = true;
		}
//
//		public int calculate(int sampleRate, short [] audioData){
//
//			int numSamples = audioData.length;
//			int numCrossing = 0;
//			for (int p = 0; p < numSamples-1; p++)
//			{
//				if ((audioData[p] > 0 && audioData[p + 1] <= 0) || 
//						(audioData[p] < 0 && audioData[p + 1] >= 0))
//				{
//					numCrossing++;
//				}
//			}
//
//			float numSecondsRecorded = (float)numSamples/(float)sampleRate;
//			float numCycles = numCrossing/2;
//			float frequency = numCycles/numSecondsRecorded;
//
//			return (int)frequency;
//		}

		public void displayMsg(final String message)
		{
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					//freqText.setText(message + "\n");
				}
			});
		}

		public void drawImage(final double[] transform) 
		{
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					canvas.drawColor(Color.BLACK);
					int freq = 0;
					for (int i = 0; i < transform.length; i++) {
						int x = i;
						int downy = (int) (100 - (transform[i] * 10));
						int upy = 100;
						freq = Math.abs(upy-downy);
						canvas.drawLine(x, downy, x, upy, paint);
					}
					max2 = (freq > max2)?freq:max2;
					min2 = (freq < min2 && freq != 0)?freq:min2;
					freqText.setText("Frequency : "+freq + "\t Max : " + max2 + "\t Min : "+ min2);
					signalFrequency.invalidate();
				}
			});
		}

	}

}
