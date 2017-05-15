import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * Created by ks on 14/04/16.
 */
public class SingerThread extends Thread {
    //Constants
    public static final int distanceTolerance = 2;
    public static final int symbolTolerance = 1;
    public static final int bufferSize = 5;
    public static final int sleepTime = 200; // in ms

    //Variables
    private CountDownLatch latch;
    public volatile boolean stop = false;
    public volatile float hits = 0;
    public volatile float total = 0;
    public volatile ArrayList<String> songBuffer = new ArrayList<>();
    public volatile ArrayList<String> micBuffer = new ArrayList<>();

    //Constructor
    public SingerThread(CountDownLatch latch)
    {
        this.latch = latch;
    }

    public static int symbolDifference(char songNote, char micNote){
        if (micNote!='N' && songNote!='N'){
            int difference = java.lang.Math.abs(songNote - micNote);

            if (difference > 3){
                difference = 7 - difference;
            }
            return difference;
        }
        return -1;
    }

    public static boolean compareSymbols(char songNote, char micNote){
        int difference = symbolDifference(songNote,micNote);
        if (difference >= 0 && difference <= symbolTolerance){
            return true;
        }
        else{
            return false;
        }
    }

    public boolean compareDistances(String songNote1, String songNote2, String micNote1, String micNote2){
        //Check for silences
        if (songNote1.charAt(0)=='N' && micNote1.charAt(0)=='N'){
            if (songNote2.charAt(0)!='N' && micNote2.charAt(0)!='N'){
                return true;
            }
        }
        if (songNote2.charAt(0)=='N' && micNote2.charAt(0)=='N'){
            if (songNote1.charAt(0)!='N' && micNote1.charAt(0)!='N'){
                return true;
            }
        }

        //Otherwise, compare distances
        try{
            int micDistance = java.lang.Math.abs(Note.parseNoteSymbol(micNote1) - Note.parseNoteSymbol(micNote2));
            int songDistance = java.lang.Math.abs(Note.parseNoteSymbol(songNote1) - Note.parseNoteSymbol(songNote2));
            if (java.lang.Math.abs(micDistance - songDistance)<= distanceTolerance){
                return true;
            }
            else{
                return false;
            }
        }catch (Exception e){
            return false;
        }
    }

    public void updateSurroundingNotes(String micNote, String songNote){
        songBuffer.remove(0);
        songBuffer.add(songNote);
        micBuffer.remove(0);
        micBuffer.add(micNote);
    }

    public boolean mustSing(){
        boolean result = false;
        for (int i = 0; i<songBuffer.size();i++){
            if (songBuffer.get(i).charAt(0)!='N'){
                result = true;
            }
        }
        return result;
    }

    public void updateScore(String micNote, String songNote){
        //Get surrounding notes
        updateSurroundingNotes(micNote, songNote);

        //Check if you are expected to sing
        if (mustSing()){
            boolean hitSymbol = false;
            boolean hitDistance = false;

            for (int i = 0; i<bufferSize; i++){
                for (int j = 0; j<bufferSize; j++){
                    //Compare symbols
                    if (compareSymbols(songBuffer.get(i).charAt(0),micBuffer.get(j).charAt(0))){
                        hitSymbol = true;
                    }

                    //Compare distances
                    if (i!=0 && j!=0){
                        if (compareDistances(songBuffer.get(i-1),songBuffer.get(i),micBuffer.get(j-1),micBuffer.get(j))){
                            hitDistance = true;
                        }
                    }
                }
            }
            //Check if singer is singing
            if (hitSymbol){hits+=0.5;}
            if (hitDistance){hits+=0.5;}

            //Update total
            total += 1.0;
        }
    }

    @Override
    public void run(){
        try{
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Initialize buffers
        songBuffer.clear();
        micBuffer.clear();
        for (int i = 0; i<bufferSize;i++){
            songBuffer.add("N0");
            micBuffer.add("N0");
        }

        while(!stop){
            //Sleep 20ms
            try{
                sleep(sleepTime);
                if (Main.mediaPlayer != null){
                    //Get both notes
                    double timestamp = Main.mediaPlayer.getCurrentTime().toMillis();
                    String micNote = Main.getNoteFromMic();
                    timestamp = (timestamp + Main.mediaPlayer.getCurrentTime().toMillis())/2;
                    //String micNote = Main.getNoteFromSong(timestamp+50);
                    String songNote = Main.getNoteFromSong(timestamp);

                    //Print message in screen
                    String message = String.format("Time: %.2fms. ", timestamp) + "Mic note: " + micNote + ". Expected note: " + songNote + ".\n";
                    System.out.println(message);

                    //Compare notes
                    updateScore(micNote, songNote);
                }
            }
            catch (Exception e){
                e.printStackTrace();
                break;
            }
        }
    }
}