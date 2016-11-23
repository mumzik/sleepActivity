package mumzik.ru.inertialsensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int DATA_READING_PERIOD = 200;//milliseconds
    private static final int PERIOD_OF_NORMALIZATION = 10;//seconds
    private static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_NORMAL;
    private static final double ACCELERATION_THESHOLD = 5;//  m/s^2
    private static final double ROTATE_THRESHOLD = 0.1 * Math.PI * 2;//   rad/s
    private static final String SD_PATH = "/mnt/ext_sdcard/";
    private final static String DIR = "sleep_activity";
    private final static String OUTPUT_FILE_NAME = "output.txt";

    private ArrayList<Integer> sleepActivityStatistic = new ArrayList();//TODO в плюсах придется реализовывать это вручную
    BufferedWriter writer;
    private SensorManager sensorManager;
    private Sensor accelerometr, gyroscope;
    private float[] lastAccReadings = new float[3];
    private float[] currentAccReadings = new float[3];
    private float[] currentGyrReadings = new float[3];
    private Handler guiHandler = new Handler();//only for GUI
    private TextView output;
    private Thread readAndAnalyseThread;

    private SensorEventListener accelerometrListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            currentAccReadings = event.values;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };


    private SensorEventListener gyroscopeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            currentGyrReadings = event.values;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometr = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(accelerometrListener, accelerometr, SENSOR_DELAY);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(gyroscopeListener, gyroscope, SENSOR_DELAY);

        output=(TextView) findViewById(R.id.textView);

        /*Main loop Thread*/
        final Runnable readAndAnalyse = new Runnable() {
            @Override
            public void run() {

                if (Environment.getExternalStorageState().equals(
                        Environment.MEDIA_MOUNTED)) {
                    File folder = new File(SD_PATH + DIR);
                    if (!folder.exists())
                        folder.mkdirs();
                    final File file = new File(folder, OUTPUT_FILE_NAME);
                    try {
                        if (!file.exists())
                            file.createNewFile();

                        guiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this,"writing to: "+file.getAbsoluteFile(),Toast.LENGTH_SHORT).show();
                            }
                        });

                        writer = new BufferedWriter(new FileWriter(file));
                        writer.write("start time utc: "+System.currentTimeMillis()+"\n");
                        writer.write("normalization period's duration: "+PERIOD_OF_NORMALIZATION+"s\n");


                        int periodTimer = 0;//time in DATA_READING_PERIODs after current period of noramlization has started
                        int periodActivityCounter = 0;//activity count per current period of noramlization
                        while (!Thread.currentThread().isInterrupted()) {
                    /*activity detection*/

                            for (int axis = 0; axis < 3; axis++) {
                                if (Math.abs(currentAccReadings[axis] - lastAccReadings[axis]) > ACCELERATION_THESHOLD) {
                                    periodActivityCounter++;
                                    break;
                                }
                                if (Math.abs(currentGyrReadings[axis]) > ROTATE_THRESHOLD) {
                                    periodActivityCounter++;
                                    break;
                                }
                                lastAccReadings[axis] = currentAccReadings[axis];
                            }

                            if (periodTimer < (PERIOD_OF_NORMALIZATION * 1000 / DATA_READING_PERIOD))
                                periodTimer++;
                            else
                        /*start new period*/ {
                                final int activityPercent = periodActivityCounter * 100 / (PERIOD_OF_NORMALIZATION * 1000 / DATA_READING_PERIOD);
                                writer.write(activityPercent+"\n");
                                writer.flush();
                                guiHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        output.setText("activity over the last "+PERIOD_OF_NORMALIZATION+"s: "+activityPercent+"%");
                                    }
                                });
                                sleepActivityStatistic.add(periodActivityCounter);
                                periodActivityCounter = 0;
                                periodTimer = 0;
                            }

                            try {
                                Thread.sleep(DATA_READING_PERIOD);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally{
                        if (writer!=null)
                            try {
                                writer.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                    }
                }
            }

        };
        final Button writeButton=(Button) findViewById(R.id.button);
        writeButton.setText("start writing");
        writeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (writeButton.getText().equals("start writing")) {
                    readAndAnalyseThread=new Thread(readAndAnalyse);
                    readAndAnalyseThread.start();
                    writeButton.setText("finish writing");
                }else {
                    readAndAnalyseThread.interrupt();
                    writeButton.setText("start writing");
                }
            }
        });
    }
}
