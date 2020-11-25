package com.example.orientationmouse;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FirstFragment extends Fragment implements SensorEventListener {

    private Handler handler = new Handler();
    private Runnable r;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private final float[] gVal = new float[3];
    private final float[] bVal = new float[3];

    private final float[] orientationAngles = new float[3];

    private final float[] calibAngles = new float[3];
    float[] deltaAngles = new float[3];

    private final float[] eCompass = new float[3];

    private long last_time = 0;

    private static final String TAG = "FirstFragment";

    private final float RAD2DEG = (float) 57.2958;

    @Override
    public void onPause() {
        handler.removeCallbacks(r);
        super.onPause();
    }

    @Override
    public void onResume() {
        handler.postDelayed(r, 1000);
        super.onResume();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }

        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.calib).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                System.arraycopy(orientationAngles, 0,
                        calibAngles, 0,
                        orientationAngles.length);
            }
        });

        r = new Runnable() {
            public void run() {
                TextView x = view.findViewById(R.id.sensor_x);
                TextView y = view.findViewById(R.id.sensor_y);
                TextView z = view.findViewById(R.id.sensor_z);

                x.setText(Integer.toString((int) deltaAngles[0]));
                y.setText(Integer.toString((int) deltaAngles[1]));
                z.setText(Integer.toString((int) deltaAngles[2]));

                handler.postDelayed(this, 100);
            }
        };

        handler.postDelayed(r, 100);
    }

    private float lowPassFilter(float oldVal, float newVal) {
        final float alpha = (float) 0.5;
        return (float) ((1.0 - alpha) * oldVal + alpha * newVal);
    }

    private float iTrig(float ix, float iy) {
        return (float) (ix / Math.sqrt(ix * ix + iy * iy));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long now = event.timestamp;     // ns

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gVal,
                    0, gVal.length);
            return;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, bVal,
                    0, bVal.length);
        }

        // Math ported from:
        // https://www.nxp.com/docs/en/application-note/AN4248.pdf

        float iSin, iCos;
        float iBfx, iBfy, iBfz;

//        /* subtract the hard iron offset */
//        iBpx -= iVx; /* see Eq 16 */
//        iBpy -= iVy; /* see Eq 16 */
//        iBpz -= iVz; /* see Eq 16 */

        /* calculate current roll angle Phi */
        eCompass[0] = (float) (Math.atan2(gVal[1], gVal[2]) * RAD2DEG * 100.0);/* Eq 13 */

        /* calculate sin and cosine of roll angle Phi */
        iSin = (float) (iTrig(gVal[1], gVal[2]) * RAD2DEG * 100.0); /* Eq 13: sin = opposite / hypotenuse */
        iCos = (float) (iTrig(gVal[2], gVal[1]) * RAD2DEG * 100.0); /* Eq 13: cos = adjacent / hypotenuse */

//        Log.d("MATH", "iSin: " + iSin +
//                " iCos: " + iCos);

        /* de-rotate by roll angle Phi */
        iBfy = (float) ((bVal[1] * iCos - bVal[2] * iSin) / Math.pow(2, 15));/* Eq 19 y component */
        bVal[2] = (float) ((bVal[1] * iSin + bVal[2] * iCos) / Math.pow(2, 15));/* Bpy*sin(Phi)+Bpz*cos(Phi)*/
        gVal[2] = (float) ((gVal[1] * iSin + gVal[2] * iCos) / Math.pow(2, 15));/* Eq 15 denominator */

        /* calculate current pitch angle Theta */
        eCompass[1] = (float) (Math.atan2(-gVal[0], gVal[2]) * RAD2DEG * 100.0);/* Eq 15 */

        /* restrict pitch angle to range -90 to 90 degrees */
        if (eCompass[1] > 9000.0)
            eCompass[1] = (float) 18000.0 - eCompass[1];
        if (eCompass[1] < -9000.0)
            eCompass[1] = (float) -18000.0 - eCompass[1];

        /* calculate sin and cosine of pitch angle Theta */
        iSin = (float) (-iTrig(gVal[0], gVal[2]) * RAD2DEG * 100.0); /* Eq 15: sin = opposite / hypotenuse */
        iCos = (float) (iTrig(gVal[2], gVal[0]) * RAD2DEG * 100.0); /* Eq 15: cos = adjacent / hypotenuse */

        /* correct cosine if pitch not in range -90 to 90 degrees */
        if (iCos < 0)
            iCos = -iCos;

        /* de-rotate by pitch angle Theta */
        iBfx = (float) ((bVal[0] * iCos + bVal[2] * iSin) / Math.pow(2, 15)); /* Eq 19: x component */
        iBfz = (float) ((-bVal[0] * iSin + bVal[2] * iCos) / Math.pow(2, 15));/* Eq 19: z component */

        /* calculate current yaw = e-compass angle Psi */
        eCompass[2] = (float) (Math.atan2(-iBfy, iBfx) * RAD2DEG * 100.0); /* Eq 22 */

        for (int i = 0; i < 3; i++) {
            eCompass[i] /= 100.0;
            orientationAngles[i] = lowPassFilter(orientationAngles[i], eCompass[i]);

            // TODO: Calculate wraparound angle when on negative boundary.
            deltaAngles[i] = orientationAngles[i] - calibAngles[i];
        }

//        Log.d("RAW", "roll: " + (int) eCompass[0] +
//                " pitch: " + (int) eCompass[1] +
//                " yaw: " + (int) eCompass[2]);
//        Log.d("LPFILT", "roll: " + (int) orientationAngles[0] +
//                " pitch: " + (int) orientationAngles[1] +
//                " yaw: " + (int) orientationAngles[2]);

        Log.d("DELTA", "roll: " + (int) calibAngles[0] +
                " pitch: " + (int) calibAngles[1] +
                " yaw: " + (int) calibAngles[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}