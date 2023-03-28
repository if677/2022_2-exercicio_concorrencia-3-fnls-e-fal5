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

    // variavel para fazer o lock da bitstream
    private ReentrantLock bitstreamLock = new ReentrantLock();

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
    private boolean nextMusic = false;
    private boolean previousMusic = false;
    private boolean isPlaying = false;

    private final ActionListener buttonListenerPlayNow = e -> {
        // caso tenha mudado de musica antes de terminar a musica anterior
        threadInterrupt(playThread, bitstream, device);

        // iniciar a thread e começar a tocar a musica
        playThread = new Thread(this::playNow);
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

    private final ActionListener buttonListenerNext = e -> {
        threadInterrupt(playThread, bitstream, device);
        nextMusic = true;
        playThread = new Thread(this::playNow);
        playThread.start();
    };

    private final ActionListener buttonListenerPrevious = e -> {
        threadInterrupt(playThread, bitstream, device);
        previousMusic = true;
        playThread = new Thread(this::playNow);
        playThread.start();
    };

    private final ActionListener buttonListenerShuffle = e -> {};
    private final ActionListener buttonListenerLoop = e -> {};
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        int nextTime;
        int nextFrame;
        @Override
        public void mouseReleased(MouseEvent e) {
            Thread updateFrame = new Thread(() -> {
                bitstreamLock.lock();

                // se o proximo frame for antes do atual, precisa recomecar a musica
                if (nextFrame < currentFrame) {
                    // fechar device e bitstream
                    try {
                        if (bitstream != null) {
                            bitstream.close();
                            device.close();
                        }
                    } catch (BitstreamException ex) {
                        throw new RuntimeException(ex);
                    }

                    // criar novo device e bitstream
                    try {
                        device = FactoryRegistry.systemRegistry().createAudioDevice();
                        device.open(decoder = new Decoder());
                        bitstream = new Bitstream(currSong.getBufferedInputStream());
                    } catch (JavaLayerException | FileNotFoundException ex) {
                        throw new RuntimeException(ex);
                    }

                    bitstreamLock.unlock();
                    currentFrame = 0;
                }

                try {
                    skipToFrame(window.getScrubberValue());
                } catch (BitstreamException ex) {
                    throw new RuntimeException(ex);
                }

                EventQueue.invokeLater(() -> {
                    window.setTime(nextTime, (int) currSong.getMsLength());
                });
            });

            updateFrame.start();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            nextTime = window.getScrubberValue();
            nextFrame = (int) (nextTime/currSong.getMsPerFrame());
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
        bitstreamLock.lock();
        try{
            if (device != null) {
                Header h = bitstream.readFrame();
                if (h == null) return false;

                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
                device.write(output.getBuffer(), 0, output.getBufferLength());
                bitstream.closeFrame();
                currentFrame++;
            }
            return true;
        } finally {
            bitstreamLock.unlock();
        }

    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO: Is this thread safe?
        bitstreamLock.lock();

        try {
            Header h = bitstream.readFrame();
            if (h == null) return false;
            bitstream.closeFrame();
            currentFrame++;
            return true;
        } finally {
            bitstreamLock.unlock();
        }
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO: Is this thread safe?
        bitstreamLock.lock();
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
        bitstreamLock.unlock();
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
        t.interrupt();

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

    private static void threadInterrupt(Thread t, Bitstream b, AudioDevice d) {
        if (b != null) {
            t.interrupt();

            boolean naoInterrompido = true;
            while(naoInterrompido) {
                if (t.isInterrupted()) {
                    try {
                        b.close();
                        d.close();
                    } catch (BitstreamException ex) {
                        throw new RuntimeException(ex);
                    }

                    naoInterrompido = false;
                }
            }
        }
    }

    private void playNow() {
        // setando o frame para o começo da musica
        currentFrame = 0;
        playPause = 1;
        isPlaying = true;

        // selecionando a musica
        if(nextMusic) index++;
        else if(previousMusic) index--;
        else index = window.getIdx();
        currSong = playlist.get(index);
        nextMusic = false;
        previousMusic = false;


        // inicializar os objetos para reproduzir a musica
        try {
            device = FactoryRegistry.systemRegistry().createAudioDevice();
            device.open(decoder = new Decoder());
            bitstream = new Bitstream(currSong.getBufferedInputStream());

        } catch (JavaLayerException | FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }

        // exibir informações da musica atual
        window.setPlayingSongInfo(currSong.getTitle(), currSong.getAlbum(), currSong.getArtist());

        // tocar a musica
        while(true) {
            if(playPause == 1){
                try {
                    // mostrar o tempo da musica e habilitar os botões
                    window.setTime((currentFrame * (int) currSong.getMsPerFrame()), (int) currSong.getMsLength());
                    window.setPlayPauseButtonIcon(playPause);
                    window.setEnabledPlayPauseButton(true);
                    window.setEnabledStopButton(true);
                    window.setEnabledPreviousButton(index != 0);
                    window.setEnabledNextButton(index != playlist.size()-1);

                    // resetar o miniplayer e fechar os objetos quando a musica acabar
                    if (!playNextFrame()) {
                        bitstream.close();
                        device.close();
                        isPlaying = false;
                        window.resetMiniPlayer();
                        playThread.interrupt();
                    }
                } catch (JavaLayerException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }


    //</editor-fold>
}
