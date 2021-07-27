package Spleeter.test;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;


import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.kbeanie.multipicker.api.AudioPicker;
import com.kbeanie.multipicker.api.Picker;
import com.kbeanie.multipicker.api.callbacks.AudioPickerCallback;
import com.kbeanie.multipicker.api.entity.ChosenAudio;
import com.opensooq.supernova.gligar.GligarPicker;

import Spleeter.test.databinding.ActivityMainBinding;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.navigation.Navigation;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import android.util.Log;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 102;
    AudioPicker audioPicker;
    Interpreter tflite;
    ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // testArray();



        audioPicker = new AudioPicker(this);
        initListeners();
        checkPermission();
        initSpleeter();



    }

    private void testArray() {
        final byte[] data = new byte[] {
                64, 73, 15, -48, 127, 127, -1, -1, 0, 0, 0, 1, 0, 0, 0, 0
        };

        final FloatBuffer fb = ByteBuffer.wrap(data).asFloatBuffer();
        final float[] dst = new float[fb.capacity()];
        fb.get(dst); // Copy the contents of the FloatBuffer into dst
        Log.e(TAG, "testArray: Size is "+ dst.length );
        for (int i = 0; i < dst.length; i++) {
            Log.e(TAG, "testArray: "+dst[i] );
            if (i == dst.length - 1) {

            } else {
                Log.e(TAG, ", ");
            }
        }
    }


    public void initSpleeter() {
        try {
            Interpreter.Options tfliteOptions = new Interpreter.Options();
            tfliteOptions.setNumThreads(2);
            tflite = new Interpreter(loadModelFile(this), tfliteOptions);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "onCreate: Error: " + e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Memory-map the model file in Assets.
     */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(getModelPath());
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public String getModelPath() {
        return "stems.tflite";
    }

    public void mp3ToFloat(String path) throws BitstreamException, DecoderException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream(1024);
        FileInputStream mp3Source = null;
        try {
            mp3Source = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "onActivityResult: Error " + e.getMessage());
        }
        Bitstream bitStream = new Bitstream(mp3Source);
        Decoder decoder = new Decoder();
        final int READ_THRESHOLD = 2147483647;

        Header frame;
        int framesReaded = 0;
        while (framesReaded++ <= READ_THRESHOLD && (frame = bitStream.readFrame()) != null) {
            SampleBuffer sampleBuffer = (SampleBuffer) decoder.decodeFrame(frame, bitStream);
            short[] pcmChunk = sampleBuffer.getBuffer();

            for (short s : pcmChunk) {
                outStream.write(s & 0xff);
                outStream.write((s >> 8) & 0xff);
            }
            //   audioTrack.write(outStream.toByteArray(), 0, outStream.toByteArray().length); //playing byte array with audiotrack to be sure
            try {
                outStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "mp3ToFloat: Error");
            }
            bitStream.closeFrame();
        }


        byte[] byteArray = outStream.toByteArray();
        byte[] sampleFloatArray = Arrays.copyOfRange(byteArray, 0, 640000); //we fetch small audio sample


        float[] fArr = byteToFloat(byteArray);
        float[] fArr2 = byteToFloat2(byteArray);
        byte[] bytess = floatToByte(fArr);
      //  playAudio(bytess);
      //  playAudioFloat(fArr);
       // Log.e(TAG, "mp3ToFloat: Float Array Size is "+fArr.length );


        DataType imageDataType  = tflite.getInputTensor(0).dataType();
        tflite.resizeInput(0, new int[]{320000, 2});






        float floats[] = new float[640000];
        for (int i = 0; i < 640000-4; i+=2) {// loop thrugh the 128 float arrays, each float array is of size 129
            float a0 = sampleFloatArray[i];
            float a1 = sampleFloatArray[i+1];
//            Log.e(TAG, "mp3ToFloat: float[0] = "+ floats[0] );
//            Log.e(TAG, "mp3ToFloat: float[1] = "+ floats[1] );
            floats[i/2] = a0;
            floats[320000+(i/2)] = a1;

        }

        int[] probabilityShape =  tflite.getInputTensor(0).shape();
        DataType probabilityDataType =  tflite.getInputTensor(0).dataType();



        TensorBuffer tensorBuffer = TensorBuffer.createDynamic(imageDataType);
        tensorBuffer.loadArray(floats, probabilityShape);
        ByteBuffer inByteBuffer = tensorBuffer.getBuffer();


        Object[] input = new Object[1];
        input[0] = inByteBuffer;

       // ByteBuffer instrumentalBuffer = ByteBuffer.allocateDirect(2560000);
        //ByteBuffer vocalBuffer = ByteBuffer.allocateDirect(2560000);



         TensorBuffer outputTensorBufferI =
                TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

        TensorBuffer outputTensorBufferV =
                TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

        Map outputs = new TreeMap<>();
        outputs.put(0, outputTensorBufferI.getBuffer());
        outputs.put(1, outputTensorBufferV.getBuffer());



        Log.e(TAG, "mp3ToFloat: start running");
        tflite.runForMultipleInputsOutputs(input, outputs);
        Log.e(TAG, "mp3ToFloat: finished");

       // byte[] instrumental = instrumentalBuffer.array();
        //byte[] vocal = vocalBuffer.array();
        //Log.e(TAG, "mp3ToFloat: "+ instrumental.length );
//        for (int i=0; i<instrumental.length;i++) {
//            Log.e(TAG, "mp3ToFloat: "+instrumental[i] );
//        }

//        byte[] b = new byte[320000];
//        for (int i = 0; i < 640000-4; i+=2) {// loop thrugh the 128 float arrays, each float array is of size 129
//            byte a0 = instrumental[i/2];
//            byte a1 = instrumental[320000+(i/2)];
////            Log.e(TAG, "mp3ToFloat: float[0] = "+ floats[0] );
////            Log.e(TAG, "mp3ToFloat: float[1] = "+ floats[1] );
//            b[i] = a0;
//            b[(i)+1] = a1;
//
//        }

        playAudio(outputTensorBufferI.getBuffer().array());
    }

    private float[] byteToFloat2(byte[] byteArray) {
        FloatBuffer l = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        final FloatBuffer fb = ByteBuffer.wrap(byteArray).asFloatBuffer();
        final float[] dst = new float[fb.capacity()];
        fb.get(dst);
        return dst;
    }

    private float[] byteToFloat(byte[] byteArray) {
        ByteArrayInputStream bas = new ByteArrayInputStream(byteArray);
        DataInputStream ds = new DataInputStream(bas);
        float[] fArr = new float[byteArray.length / 4];  // 4 bytes per float
        for (int i = 0; i < fArr.length; i++) {
            try {
                fArr[i] = ds.readFloat();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return fArr;
    }

    private byte[] floatToByte(float[] floatArray) {
        ByteArrayOutputStream bas2 = new ByteArrayOutputStream();
        DataOutputStream ds2 = new DataOutputStream(bas2);
        for (float f : floatArray) {
            try {
                ds2.writeFloat(f);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        byte[] bytes = bas2.toByteArray();
        return bytes;
    }

    private void playAudioFloat(float[] l) {
        final int SAMPLE_RATE = 48000;
        final int CHANNEL_COUNT = AudioFormat.CHANNEL_OUT_STEREO;
        final int ENCODING = AudioFormat.ENCODING_PCM_FLOAT;

        int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_COUNT, ENCODING);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .build();
        AudioTrack audioTrack = new AudioTrack(audioAttributes, audioFormat, bufferSize
                , AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        audioTrack.play();
        audioTrack.write(l, 0, l.length, AudioTrack.WRITE_BLOCKING);
    }

    private void playAudio(byte[] instrumental) {
        final int sampleRate = 44100;
        final int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);

        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                //  AudioFormat.ENCODING_PCM_FLOAT,
                minBufferSize,
                AudioTrack.MODE_STREAM);
        audioTrack.play();
        audioTrack.write(instrumental, 0, instrumental.length);
    }


    private void initListeners() {
        binding.btnOpenFile.setOnClickListener(v -> {
            selectAudioFile();
        });
    }

    public void selectAudioFile() {
        if (checkPermission()) {
            audioPicker.setAudioPickerCallback(new AudioPickerCallback() {
                @Override
                public void onAudiosChosen(List<ChosenAudio> files) {
                    // Convert Mp3 to float
                    try {
                        mp3ToFloat(files.get(0).getOriginalPath());
                    } catch (BitstreamException e) {
                        Log.e(TAG, "onActivityResult: Error " + e.getMessage());
                    } catch (DecoderException e) {
                        e.printStackTrace();
                        Log.e(TAG, "onActivityResult: Error " + e.getMessage());
                    }
                }

                @Override
                public void onError(String message) {
                    // Handle errors
                    Log.e(TAG, "onActivityResult: Error " + message);
                }
            });
            audioPicker.pickAudio();
        } else {
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == Picker.PICK_AUDIO && resultCode == RESULT_OK) {
            audioPicker.submit(data);
        }

    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        // request permission if it has not been grunted.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }
}