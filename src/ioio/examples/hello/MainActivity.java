package ioio.examples.hello;

/**************************************************************************
 * Happy version 140517A...ultrasonics and motors working
 **************************************************************************/
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.Sequencer;
import ioio.lib.api.Sequencer.ChannelConfigBinary;
import ioio.lib.api.Sequencer.ChannelCueBinary;
import ioio.lib.api.Sequencer.ChannelCueFmSpeed;
import ioio.lib.api.Sequencer.ChannelCueSteps;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.content.Intent;
import android.graphics.DashPathEffect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * This is the main activity of the HelloIOIO example application. It displays a
 * toggle button on the screen, which enables control of the on-board LED and
 * controls the VicsWagon. Modified by Vic rev 140430A
 */
public class MainActivity extends IOIOActivity {
	private ToggleButton button_;
	public UltraSonicSensor sonar;
	private TextView mText;
	private ScrollView mScroller;
	private TextToSpeech mTts;
	/**
	 * Compass stuff
	 */
	SensorManager sensorManager;
	private Sensor sensorAccelerometer;
	private Sensor sensorMagneticField;
	private float[] valuesAccelerometer;
	private float[] valuesMagneticField;
	private float[] matrixR;
	private float[] matrixI;
	private float[] matrixValues;
	private double azimuth;
	private double pitch;
	private double roll;

	/**
	 * Called when the activity is first created. Here we normally initialize
	 * our GUI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		button_ = (ToggleButton) findViewById(R.id.button);
		mText = (TextView) findViewById(R.id.logText);
		mScroller = (ScrollView) findViewById(R.id.scroller);
		// Compass stuff
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		sensorAccelerometer = sensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorMagneticField = sensorManager
				.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

		valuesAccelerometer = new float[3];
		valuesMagneticField = new float[3];

		matrixR = new float[9];
		matrixI = new float[9];
		matrixValues = new float[3];
	}

	/**
	 * This is the thread on which all the IOIO activity happens. It will be run
	 * every time the application is resumed and aborted when it is paused. The
	 * method setup() will be called right after a connection with the IOIO has
	 * been established (which might happen several times!). Then, loop() will
	 * be called repetitively until the IOIO gets disconnected
	 */
	class Looper extends BaseIOIOLooper {
		private DigitalOutput led_;// The on-board LED
		private int pulseWidth = 10;// microseconds
		private int rightStepperMotorPeriod = 60000;
		private DigitalOutput rightMotorClockPulse;
		private DigitalOutput leftMotorClockPulse;
		private DigitalOutput motorEnable; // Both motors
		private DigitalOutput rightMotorClock; // Step right motor
		private DigitalOutput leftMotorClock; // Step left motor
		private DigitalOutput motorCongtrollerReset;
		private DigitalOutput rightMotorControl; // Motor decay mode
		private DigitalOutput rightMotorDirection;
		private DigitalOutput leftMotorDirection;
		private DigitalOutput motorControllerControl;// Decay mode selector,
														// high = slow decay,
														// low = fast decay
		private static final int MOTOR_ENABLE_PIN = 3;// Low turns off all power
														// to both motors
		private static final int MOTOR_RIGHT_DIRECTION_OUTPUT_PIN = 20;// High =
																		// clockwise,
																		// low =
																		// ccw
		private static final int MOTOR_LEFT_DIRECTION_OUTPUT_PIN = 21;
		private static final int MOTOR_CONTROLLER_CONTROL_PIN = 6;// For both
		private static final int REAR_STROBE_ULTRASONIC_OUTPUT_PIN = 14;// output
																		// from
																		// ioio
																		// board
		private static final int MOTOR_HALF_FULL_STEP_PIN = 7;// For both motors
		private static final int MOTOR_RESET = 22;// For both motors
		private static final int MOTOR_CLOCK_LEFT_PIN = 27;
		private static final int MOTOR_CLOCK_RIGHT_PIN = 28;
		private Sequencer sequencer_;
		private Sequencer.ChannelCueBinary stepperDirCue_ = new ChannelCueBinary();
		private Sequencer.ChannelCueSteps stepperStepCue_ = new ChannelCueSteps();
		private Sequencer.ChannelCueFmSpeed stepperFMspeedCue_ = new ChannelCueFmSpeed();
		private Sequencer.ChannelCue[] cue_ = new Sequencer.ChannelCue[] { stepperFMspeedCue_ };
		final ChannelConfigBinary stepperDirConfig = new Sequencer.ChannelConfigBinary(
				false, false, new DigitalOutput.Spec(
						MOTOR_RIGHT_DIRECTION_OUTPUT_PIN));
		private int rightMotorSpeed;
		private DigitalOutput halfFull;
		private DigitalOutput reset; // Must be true for motors to run.
		private DigitalOutput control;// Decay mode selector high = slow, low =
										// fast mode

		// final ChannelConfigSteps stepperStepConfig = new
		// ChannelConfigSteps(new DigitalOutput.Spec(MOTOR_CLOCK_RIGHT_PIN));
		// final ChannelConfigFmSpeed stepperFMspeedConfig = new
		// ChannelConfigFmSpeed(Clock.CLK_2M, 10, new
		// DigitalOutput.Spec(MOTOR_CLOCK_RIGHT_PIN));
		// final ChannelConfig[] config = new ChannelConfig[]
		// {stepperFMspeedConfig};

		/**
		 * Called every time a connection with IOIO has been established.
		 * Typically used to open pins.
		 * 
		 * @throws ConnectionLostExceptio
		 *             when IOIO connection is lost. when IOIO connection lost.
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#setup()
		 */
		@Override
		protected void setup() throws ConnectionLostException {
			sonar = new UltraSonicSensor(ioio_);
			led_ = ioio_.openDigitalOutput(0, true);
			rightMotorDirection = ioio_.openDigitalOutput(20, true);// vicswagon
																	// goes
																	// forward
			leftMotorDirection = ioio_.openDigitalOutput(21, false);
			motorCongtrollerReset = ioio_.openDigitalOutput(22, true);
			motorEnable = ioio_.openDigitalOutput(3, true);// Must be true for
															// motors to run
			rightMotorClock = ioio_.openDigitalOutput(28, false);// Each pulse
																	// moves
																	// motor one
																	// step
			leftMotorClock = ioio_.openDigitalOutput(27, false);
			rightMotorControl = ioio_.openDigitalOutput(6, false);// Both
																	// motors,
																	// low =>
																	// fast
																	// motor
																	// decay
																	// mode
		}

		/**
		 * Called repetitively while the IOIO is connected.
		 * 
		 * @throws ConnectionLostException
		 *             when IOIO connection is lost.
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#loop()
		 */
		@Override
		public void loop() throws ConnectionLostException {
			if (button_.isChecked()) {
				led_.write(false);
				try {
					Thread.sleep(1000);
					// led_.write(true);
					// rightMotorClock.write(true);
					// rightMotorClock.write(false);
					// leftMotorClock.write(true);
					// leftMotorClock.write(false);
					sonar.read();
					log(String.valueOf(sonar.getFrontDistance() + " "
							+ sonar.getLeftDistance() + " " + getAzimuth()));

				} catch (InterruptedException e) {
				}
			} else {
				led_.write(true);
			}
		}
	}

	/**
	 * A method to create our IOIO thread.
	 * 
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}

	public void log(final String msg) {
		runOnUiThread(new Runnable() {
			public void run() {
				mText.append(msg);
				mText.append("\n");
				mScroller.smoothScrollTo(0, mText.getBottom());
			}
		});
	}

	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:
			for (int i = 0; i < 3; i++) {
				valuesAccelerometer[i] = event.values[i];
			}
			break;
		case Sensor.TYPE_MAGNETIC_FIELD:
			for (int i = 0; i < 3; i++) {
				valuesMagneticField[i] = event.values[i];
			}
			break;
		}

		boolean success = SensorManager.getRotationMatrix(matrixR, matrixI,
				valuesAccelerometer, valuesMagneticField);
		log(success + "  success");
		if (success) {
			SensorManager.getOrientation(matrixR, matrixValues);
			synchronized (this) {
				azimuth = Math.toDegrees(matrixValues[0]);
				pitch = Math.toDegrees(matrixValues[1]);
				roll = Math.toDegrees(matrixValues[2]);
			}
		}
	}
	
	/**
	 * Gets the azimuth
	 * @return the azimuth
	 */
	public synchronized double getAzimuth() {
		return azimuth;
	}

	/**
	 * Gets the pitch
	 * @return the pitch
	 */
	public synchronized double getPitch() {
		return pitch;
	}

	/**
	 * Gets the roll
	 * @return the roll
	 */
	public synchronized double getRoll() {
		return roll;
	}
//	@Override
//	public void onPause() {
//		sensorManager.unregisterListener((SensorEventListener) this, sensorAccelerometer);
//		sensorManager.unregisterListener((SensorEventListener) this, sensorMagneticField);
//		super.onPause();
//	}

//	@Override
//	protected void onResume() {
//
//		sensorManager.registerListener((SensorEventListener) this, sensorAccelerometer,
//				SensorManager.SENSOR_DELAY_NORMAL);
//		sensorManager.registerListener((SensorEventListener) this, sensorMagneticField,
//				SensorManager.SENSOR_DELAY_NORMAL);
//		super.onResume();
//	}
}