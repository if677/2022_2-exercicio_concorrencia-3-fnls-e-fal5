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
import java.util.ArrayList;
import java.util.Arrays;

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

    // variaveis para passar p/ o playerwindow
    private PlayerWindow window;
    private String[][] musics = new String[0][];

    // lista com as musicas a serem tocadas
    private ArrayList<Song> playlist = new ArrayList<Song>();

    // variaveis para tocar a musica
    private int currentFrame = 0;
    private int playPause = 1;
    private int index;
    private Song currSong;
    private Thread playThread;
    private boolean isPlaying = false;

    private final ActionListener buttonListenerPlayNow = e -> {
        // caso tenha mudado de musica antes de terminar a musica anterior
        if (bitstream != null) {
            playThread.stop();
            try {
                bitstream.close();
                device.close();
            } catch (BitstreamException ex) {
                throw new RuntimeException(ex);
            }
        }

        // setando o frame para o começo da musica
        currentFrame = 0;
        playPause = 1;
        isPlaying = true;

        // selecionando a musica
        index = window.getIdx();
        currSong = playlist.get(index);

        // inicializar os objetos para reproduzir a musica
        try {
            this.device = FactoryRegistry.systemRegistry().createAudioDevice();
            this.device.open(this.decoder = new Decoder());
            this.bitstream = new Bitstream(currSong.getBufferedInputStream());

        } catch (JavaLayerException | FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }

        // exibir informações da musica atual
        window.setPlayingSongInfo(currSong.getTitle(), currSong.getAlbum(), currSong.getArtist());

        // iniciar a thread e começar a tocar a musica
        playThread = new Thread(() -> {
            while(true) {
                if(playPause == 1){
                    try {
                        // mostrar o tempo da musica e habilitar os botões
                        window.setTime((currentFrame * (int) currSong.getMsPerFrame()), (int) currSong.getMsLength());
                        window.setPlayPauseButtonIcon(playPause);
                        window.setEnabledPlayPauseButton(true);
                        window.setEnabledStopButton(true);

                        // resetar o minplayer e fechar os objetos quando a musica acabar
                        if (!playNextFrame()) {
                            bitstream.close();
                            device.close();
                            isPlaying = false;
                            window.resetMiniPlayer();
                            playThread.stop();
                        }
                    } catch (JavaLayerException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });

        playThread.start();
    };

    private final ActionListener buttonListenerRemove = e -> {
        // pega o index da música a ser removida
        int idx = window.getIdx();
        // caso ele esteja tocando, para a reprodução e reseta a janela
        if(idx == index && isPlaying) stopMusic(playThread, bitstream, device, window, isPlaying);
        // diminui o index da música tocando se uma música antes dela for removida
        if(idx < index) index--;
        // remove a música da playlist
        playlist.remove(idx);
        // remove a música da lista de músicas
        musics = removeMusic(musics, idx);
        // atualiza a fila
        this.window.setQueueList(musics);
    };

    private final ActionListener buttonListenerAddSong = e -> {
        try {
            // escolhe um arquivo mp3
            Song music = this.window.openFileChooser();
            // adciona na playlist
            playlist.add(music);
            // coleta as informações da música
            String[] musicInfo = music.getDisplayInfo();
            // pega o tamanho da lista de músicas
            int size = musics.length;
            // aumenta o tamanho da lista em 1
            musics = Arrays.copyOf(musics,size + 1);
            // adciona a nova música na lista
            musics[size] = musicInfo;
            // atualiza a fila
            this.window.setQueueList(musics);
        }
        catch(IOException | BitstreamException | UnsupportedTagException | InvalidDataException ex) {
            throw new RuntimeException(ex);
        }


    };
    private final ActionListener buttonListenerPlayPause = e -> {
        if (playPause == 1) playPause = 0; //caso o botão esteja habilitado e como pause, muda para play
        else playPause = 1; //caso esteja como play, muda para pause

        window.setPlayPauseButtonIcon(playPause); // seta a mudança

    };

    private final ActionListener buttonListenerStop = e -> {
        // caso esteja tocando alguma musica
        if (isPlaying) {
            stopMusic(playThread, bitstream, device, window, isPlaying);
        }
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
        // cria um array com 1 espaço a menos
        String[][] newList = new String[list.length - 1][];
        // copia os elementos até o do index a ser removido
        System.arraycopy(list, 0, newList, 0, idx);
        // copia os elementos depois do index a ser removido
        System.arraycopy(list, idx + 1, newList, idx, list.length - idx - 1);
        // retorna o array sem o elemento de index indicado
        return newList;
    }

    private static void stopMusic(Thread t, Bitstream b, AudioDevice d, PlayerWindow w, boolean playing){
        t.stop();

        try {
            b.close();
            d.close();
        } catch (BitstreamException ex) {
            throw new RuntimeException(ex);
        }

        w.setPlayPauseButtonIcon(0);
        w.setEnabledPlayPauseButton(false);
        w.setEnabledStopButton(false);
        playing = false;
        w.resetMiniPlayer();
    }


    //</editor-fold>
}
