package com.example.xavi.immersivepointer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class MainActivity extends AppCompatActivity {
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final String TAG = "ImmersivePointer";

    private GLSurfaceView mGLSurfaceView;
    private SensorManager mSensorManager;
    private MyRenderer mRenderer;

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothCommService mCommService;
    private String mConnectedDeviceName;

    private final float[] mPointerRotationMatrix = new float[16];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get an instance of the SensorManager
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        // Create our Preview view and set it as the content of our Activity
        mRenderer = new MyRenderer();
        mGLSurfaceView = new GLSurfaceView(this);
        mGLSurfaceView.setRenderer(mRenderer);
        setContentView(mGLSurfaceView);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (mBluetoothAdapter != null) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                // Otherwise, setup the chat session
            } else if (mCommService == null) {
                setupComm();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCommService != null) {
            mCommService.stop();
        }
    }

    @Override
    protected void onResume() {
        // Ideally a game should implement onResume() and onPause()
        // to take appropriate action when the activity looses focus
        super.onResume();
        mRenderer.start();
        mGLSurfaceView.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mCommService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mCommService.getState() == BluetoothCommService.STATE_NONE) {
                // Start the Bluetooth chat services
                mCommService.start();
            }
        }
    }

    @Override
    protected void onPause() {
        // Ideally a game should implement onResume() and onPause()
        // to take appropriate action when the activity looses focus
        super.onPause();
        mRenderer.stop();
        mGLSurfaceView.onPause();
    }

    private void connectDevice(Intent data) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mCommService.connect(device);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupComm();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                }
        }
    }

    private void setupComm() {
        // Initialize the BluetoothChatService to perform bluetooth connections
        mCommService = new BluetoothCommService(mHandler);
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothCommService.MESSAGE_READ:
                    ByteBuffer readBuf = (ByteBuffer) msg.obj;
                    processSensor(readBuf);
                    break;
                case BluetoothCommService.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(BluetoothCommService.DEVICE_NAME);
                    Toast.makeText(MainActivity.this, "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothCommService.MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this, msg.getData().getString(BluetoothCommService.TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void processSensor(ByteBuffer readBuf) {
        float[] v;
        while (readBuf.hasRemaining()) {
            byte type = readBuf.get();
            switch (type) {
                case 'M':
                    v = new float[16];
                    for (int i=0; i<16; i++)
                        v[i] = readBuf.getFloat();
                    SensorManager.getRotationMatrixFromVector(mPointerRotationMatrix, v);
                    break;
                default:
                    Log.e(TAG, "Invalid BT reading received");
                    break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_connect) {
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    class MyRenderer implements GLSurfaceView.Renderer, SensorEventListener {
        private Cube mCube;
        private Pointer mPointer;
        private Sensor mRotationVectorSensor;
        private final float[] mRotationMatrix = new float[16];
        public MyRenderer() {
            // find the rotation-vector sensor
            mRotationVectorSensor = mSensorManager.getDefaultSensor(
                    Sensor.TYPE_ROTATION_VECTOR);
            mCube = new Cube();
            mPointer = new Pointer();
            // initialize the rotation matrix to identity
            mPointerRotationMatrix[ 0] = mRotationMatrix[ 0] = 1;
            mPointerRotationMatrix[ 4] = mRotationMatrix[ 4] = 1;
            mPointerRotationMatrix[ 8] = mRotationMatrix[ 8] = 1;
            mPointerRotationMatrix[12] = mRotationMatrix[12] = 1;
        }
        public void start() {
            // enable our sensor when the activity is resumed, ask for
            // 10 ms updates.
            mSensorManager.registerListener(this, mRotationVectorSensor, 10000);
        }
        public void stop() {
            // make sure to turn our sensor off when the activity is paused
            mSensorManager.unregisterListener(this);
        }
        public void onSensorChanged(SensorEvent event) {
            // we received a sensor event. it is a good practice to check
            // that we received the proper event
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // convert the rotation-vector to a 4x4 matrix. the matrix
                // is interpreted by Open GL as the inverse of the
                // rotation-vector, which is what we want.
                SensorManager.getRotationMatrixFromVector(
                        mRotationMatrix , event.values);
            }
        }
        public void onDrawFrame(GL10 gl) {
            // clear screen
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
            // set-up modelview matrix
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();
            gl.glMultMatrixf(mRotationMatrix, 0);
            gl.glPushMatrix();
            gl.glScalef(10.f, 10.f, 10.f);
            // draw scene
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
            mCube.draw(gl);
            gl.glPopMatrix();
            // draw pointer
            gl.glMultMatrixf(mPointerRotationMatrix, 0);
            gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
            gl.glColor4f(1.f, 1.f, 1.f, 1.f);
            mPointer.draw(gl);
        }
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            // set view-port
            gl.glViewport(0, 0, width, height);
            // set projection matrix
            float ratio = (float) width / height;
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glFrustumf(-ratio, ratio, -1, 1, 1, 100);
        }
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // dither is enabled by default, we don't need it
            gl.glDisable(GL10.GL_DITHER);
            // clear screen in white
            gl.glClearColor(1,1,1,1);
        }

        class Cube {
            // initialize our cube
            private FloatBuffer mVertexBuffer;
            private FloatBuffer mColorBuffer;
            private ByteBuffer mIndexBuffer;
            public Cube() {
                final float vertices[] = {
                        -1, -1, -1,		 1, -1, -1,
                        1,  1, -1,	    -1,  1, -1,
                        -1, -1,  1,      1, -1,  1,
                        1,  1,  1,     -1,  1,  1,
                };
                final float colors[] = {
                        0,  0,  0,  1,  1,  0,  0,  1,
                        1,  1,  0,  1,  0,  1,  0,  1,
                        0,  0,  1,  1,  1,  0,  1,  1,
                        1,  1,  1,  1,  0,  1,  1,  1,
                };
                final byte indices[] = {
                        0, 4, 5,    0, 5, 1,
                        1, 5, 6,    1, 6, 2,
                        2, 6, 7,    2, 7, 3,
                        3, 7, 4,    3, 4, 0,
                        4, 7, 6,    4, 6, 5,
                        3, 0, 1,    3, 1, 2
                };
                ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length*4);
                vbb.order(ByteOrder.nativeOrder());
                mVertexBuffer = vbb.asFloatBuffer();
                mVertexBuffer.put(vertices);
                mVertexBuffer.position(0);
                ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length*4);
                cbb.order(ByteOrder.nativeOrder());
                mColorBuffer = cbb.asFloatBuffer();
                mColorBuffer.put(colors);
                mColorBuffer.position(0);
                mIndexBuffer = ByteBuffer.allocateDirect(indices.length);
                mIndexBuffer.put(indices);
                mIndexBuffer.position(0);
            }
            public void draw(GL10 gl) {
                gl.glEnable(GL10.GL_CULL_FACE);
                gl.glFrontFace(GL10.GL_CCW);
                gl.glShadeModel(GL10.GL_SMOOTH);
                gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mVertexBuffer);
                gl.glColorPointer(4, GL10.GL_FLOAT, 0, mColorBuffer);
                gl.glDrawElements(GL10.GL_TRIANGLES, 36, GL10.GL_UNSIGNED_BYTE, mIndexBuffer);
            }
        }

        class Pointer {
            // initialize our cube
            private FloatBuffer mVertexBuffer;
            private ByteBuffer mIndexBuffer;
            public Pointer() {
                final float vertices[] = {
                        -1, 0, -5,		 1, 0, -5,
                         0,-1, -5,	     0, 1, -5
                };
                final byte indices[] = {
                        0, 1,    2, 3
                };
                ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length*4);
                vbb.order(ByteOrder.nativeOrder());
                mVertexBuffer = vbb.asFloatBuffer();
                mVertexBuffer.put(vertices);
                mVertexBuffer.position(0);
                mIndexBuffer = ByteBuffer.allocateDirect(indices.length);
                mIndexBuffer.put(indices);
                mIndexBuffer.position(0);
            }
            public void draw(GL10 gl) {
                gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mVertexBuffer);
                gl.glDrawElements(GL10.GL_LINES, 4, GL10.GL_UNSIGNED_BYTE, mIndexBuffer);
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }
}
