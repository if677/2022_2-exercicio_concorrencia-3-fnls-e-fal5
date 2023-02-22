import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Player {

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice where audio samples are written to.
     */
    private AudioDevice device;
    private PlayerWindow window;
    private String[][] musics = new String[0][];

    private ArrayList<Song> playlist = new ArrayList<Song>();

    private int currentFrame = 0;
    private int playPause = 1;
    private int index;
    private Song curr_song;
    private Thread playthread;

    private final ActionListener buttonListenerPlayNow = e -> {
        // setando o frame para o começo da musica
        currentFrame = 0;

        // selecionando a musica
        index = window.getIdx();
        curr_song = playlist.get(index);

        // inicializar os objetos para reproduzir a musica
        try {
            this.device = FactoryRegistry.systemRegistry().createAudioDevice();
            this.device.open(this.decoder = new Decoder());
            this.bitstream = new Bitstream(curr_song.getBufferedInputStream());

        } catch (JavaLayerException | FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }

        // exibir informações da musica atual
        window.setPlayingSongInfo(curr_song.getTitle(), curr_song.getAlbum(), curr_song.getArtist());

        // iniciar a thread e começar a tocar a musica
        playthread = new Thread(() -> {

            while(true) {
                if(playPause == 1){
                    try {
                        window.setTime((currentFrame * (int) curr_song.getMsPerFrame()), (int) curr_song.getMsLength());
                        window.setPlayPauseButtonIcon(playPause);
                        window.setEnabledPlayPauseButton(true);
                        window.setEnabledStopButton(true);

                        // resetar o minplayer quando a musica acabar
                        if (!playNextFrame()) {
                            window.resetMiniPlayer();
                            break;
                        }
                    } catch (JavaLayerException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });

        playthread.start();
    };

    private final ActionListener buttonListenerRemove = e -> {
        int index = window.getIdx();
        playlist.remove(index);
        musics = removeMusic(musics, index);
        this.window.setQueueList(musics);
    };

    private final ActionListener buttonListenerAddSong = e -> {
        try {
            Song music = this.window.openFileChooser();
            playlist.add(music);
            String[] musicInfo = music.getDisplayInfo();
            int size = musics.length;
            musics = Arrays.copyOf(musics,size + 1);
            musics[size] = musicInfo;
            this.window.setQueueList(musics);
        }
        catch(IOException | BitstreamException | UnsupportedTagException | InvalidDataException ex) {
            throw new RuntimeException(ex);
        }



    };

    private final ActionListener buttonListenerPlayPause = e -> {
        if(playPause == 1) playPause = 0;
        else playPause = 1;

        window.setPlayPauseButtonIcon(playPause);
    };

    private final ActionListener buttonListenerStop = e -> {

    };

    private final ActionListener buttonListenerNext = e -> {};
    private final ActionListener buttonListenerPrevious = e -> {};
    private final ActionListener buttonListenerShuffle = e -> {};
    private final ActionListener buttonListenerLoop = e -> {};
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseDragged(MouseEvent e) {
        }
    };

    public Player() {
        EventQueue.invokeLater(() -> window = new PlayerWindow(
                "Apoo Player",
                musics,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)
        );
    }

    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO: Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
            currentFrame++;
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO: Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO: Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }

    private String[][] removeMusic(String[][] list, int idx){
        String[][] newList = new String[list.length - 1][];
        System.arraycopy(list, 0, newList, 0, idx);
        System.arraycopy(list, idx + 1, newList, idx, list.length - idx - 1);
        return newList;
    }
    //</editor-fold>
}
