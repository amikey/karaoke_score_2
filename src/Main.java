import be.tarsos.dsp.io.TarsosDSPAudioFloatConverter;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.CountDownLatch;

public class Main extends Application {
    //Constants
    public static final int audioBufferSize = 2048;
    public static final int bufferOverlap = 0;

    //Variables
    public static TargetDataLine microphone;
    public static SingerThread masterThread;
    public static byte[] song;
    public static double bytesPerSecond = 0.0;
    public static TarsosDSPAudioFloatConverter converter;
    public static TarsosDSPAudioFormat format;
    public static MediaPlayer mediaPlayer;


    public static AudioInputStream convertToMono(AudioInputStream stereoStream){
        try {
            int totalSize = stereoStream.available();
            AudioFormat stereoFormat = stereoStream.getFormat();
            AudioFormat monoFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);
            byte[] stereoData = new byte[totalSize];
            while ((stereoStream.read(stereoData,0,totalSize))!=-1);
            byte[] monoData = new byte[totalSize/2];
            int sampleSize = stereoFormat.getSampleSizeInBits() / 8;
            int counter = 0;

            for (int i = 0; i<totalSize/sampleSize; i++){
                if (i%2 == 0){
                    for (int j = 0; j<sampleSize;j++){
                        monoData[counter] = stereoData[i*sampleSize+j];
                        counter++;
                    }
                }
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(monoData);
            long length = (long)(monoData.length/ monoFormat.getFrameSize());
            AudioInputStream oais = new AudioInputStream(bais, monoFormat, length);

            return oais;
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static float computeScore(){
        return (masterThread.hits / masterThread.total)*100;
    }

    public static char getShortNoteFromSong(double timestamp){
        //Create pitch detector
        Yin detector = new Yin(44100,audioBufferSize);

        //Create array
        float[] audioFloatBuffer = new float[audioBufferSize];
        int offset = (int)(timestamp / 1000.0 * format.getFrameRate())*format.getFrameSize();
        if (offset + audioFloatBuffer.length*8 < song.length && offset > 0){
            converter.toFloatArray(song, offset, audioFloatBuffer, 0, audioFloatBuffer.length);
        }

        //Detect pitch
        PitchDetectionResult pitchDetectionResult = detector.getPitch(audioFloatBuffer);
        return Note.frequencyToShortNote(pitchDetectionResult.getPitch());
    }

    public static String getNoteFromSong(double timestamp){
        //Create pitch detector
        Yin detector = new Yin(44100,audioBufferSize);

        //Create array
        float[] audioFloatBuffer = new float[audioBufferSize];
        int offset = (int)(timestamp / 1000.0 * format.getFrameRate())*format.getFrameSize();
        if (offset + audioFloatBuffer.length*8 < song.length && offset > 0){
            converter.toFloatArray(song, offset, audioFloatBuffer, 0, audioFloatBuffer.length);
        }

        //Detect pitch
        PitchDetectionResult pitchDetectionResult = detector.getPitch(audioFloatBuffer);
        return Note.frequencyToNote(pitchDetectionResult.getPitch());
    }

    public static char getShortNoteFromMic(){
        //Create pitch detector
        Yin detector = new Yin(44100,audioBufferSize);
        try{
            microphone.start();
            byte[] data = new byte[audioBufferSize*8];
            int bytesRead = microphone.read(data,0,audioBufferSize*8);
            float[] audioFloatBuffer = new float[audioBufferSize];
            converter.toFloatArray(data, 0, audioFloatBuffer, 0, audioFloatBuffer.length);
            microphone.stop();

            //Detect pitch
            PitchDetectionResult pitchDetectionResult = detector.getPitch(audioFloatBuffer);
            System.out.println(pitchDetectionResult.getPitch());
            return Note.frequencyToShortNote(pitchDetectionResult.getPitch());
        }
        catch (Exception e){
            e.printStackTrace();
            return 'N';
        }
    }

    public static String getNoteFromMic(){
        //Create pitch detector
        Yin detector = new Yin(44100,audioBufferSize);
        try{
            microphone.start();
            byte[] data = new byte[audioBufferSize*8];
            int bytesRead = microphone.read(data,0,audioBufferSize*8);
            float[] audioFloatBuffer = new float[audioBufferSize];
            converter.toFloatArray(data, 0, audioFloatBuffer, 0, audioFloatBuffer.length);
            microphone.stop();

            //Detect pitch
            PitchDetectionResult pitchDetectionResult = detector.getPitch(audioFloatBuffer);
            System.out.println(pitchDetectionResult.getPitch());
            return Note.frequencyToNote(pitchDetectionResult.getPitch());
        }
        catch (Exception e){
            e.printStackTrace();
            return "N0";
        }
    }

    public static void main(String[] args) {
        try{
            //Initialize variables
            AudioInputStream wavStream;
            wavStream = AudioSystem.getAudioInputStream(new File("test.wav"));
            if (wavStream.getFormat().getChannels()==2){
                wavStream = Main.convertToMono(wavStream);
            }
            JVMAudioInputStream jvmAudioInputStream = new JVMAudioInputStream(wavStream);
            format = jvmAudioInputStream.getFormat();
            converter = TarsosDSPAudioFloatConverter.getConverter(format);
            bytesPerSecond = (wavStream.getFormat().getSampleRate() * wavStream.getFormat().getSampleSizeInBits())/ 8;
            int totalSize = wavStream.available();
            song = new byte[totalSize];
            while ((wavStream.read(song,0,totalSize))!=-1);

            //Setup microphone
            AudioFormat micFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);
            microphone = AudioSystem.getTargetDataLine(micFormat);
            microphone.open(micFormat, audioBufferSize*8);

            //Setup threads
            CountDownLatch latch = new CountDownLatch(1);
            masterThread = new SingerThread(latch);

            //Start threads
            masterThread.start();

            latch.countDown();
            launch(args);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        //Video
        Media media = new Media(new File("test.wav").toURI().toString());

        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setAutoPlay(true);
        MediaView mediaView = new MediaView(mediaPlayer);
        mediaView.setFitHeight(1024);
        mediaView.setFitWidth(800);

        mediaPlayer.setOnEndOfMedia(new Runnable(){
            @Override
            public void run(){
                //Kill master thread
                masterThread.stop = true;
                try{
                    masterThread.join(1000);
                }
                catch (Exception e){
                    e.printStackTrace();
                }

                //Print score
                float score = computeScore();
                System.out.println("Score: " + score + "%");

                //Close mic
                microphone.close();

                //Exit application
                Platform.exit();
            }
        });

        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(mediaView);

        borderPane.setStyle("-fx-background-color: Black");

        Scene scene = new Scene(borderPane, 1024, 800);
        scene.setFill(Color.BLACK);

        primaryStage.setTitle("Karaoke Smart");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
