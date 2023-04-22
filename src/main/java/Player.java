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
import java.util.*;
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
    private ReentrantLock lock = new ReentrantLock();

    // variaveis para passar p/ o playerwindow
    private PlayerWindow window;
    private String[][] musics = new String[0][];

    // lista com as músicas a serem tocadas
    private ArrayList<Song> playlist = new ArrayList<Song>();

    // variaveis para tocar a musica
    private int currentFrame = 0;
    private int playPause = 1;
    private int index;
    private Song currSong;
    private Thread playThread;
    private Thread updateFrame;
    private boolean nextMusic = false; // variável para controlar que será exibida a próxima música
    private boolean previousMusic = false; // variável para controlar que será exibida a música anterior
    private boolean isPlaying = false;
    private boolean loopQueue = false;
    boolean isShuffled = false; // variável para dizer se a pl. está em reprodução em modo aleatório
    private ArrayList<Song> previousPlaylist;
    private int currentTime;

    private final ActionListener buttonListenerPlayNow = e -> {
        // setar o frame para o começo
        currentFrame = 0;

        // iniciar a thread e começar a tocar a musica
        playNow();
    };

    private final ActionListener buttonListenerRemove = e -> {
        // pega o index da música a ser removida
        int idx = window.getIdx();
        // caso ele esteja em execução, para a reprodução e reseta a janela
        if(idx == index && isPlaying){
            stopMusic();
            if (index < playlist.size()-1){
                playThread = new Thread(this::playNow);
                playThread.start();
            }
            if(index == playlist.size()-1 && loopQueue){
                playThread = new Thread(this::playNow);
                playThread.start();
            }
        }
        // diminui o index da música tocando se uma música antes dela for removida
        if(idx < index) index--;
        // garante que o index n fique negativo por erro
        if(index < 0) index = 0;
        // remove a música da playlist
        playlist.remove(idx);
        // remove a música da lista de músicas
        musics = removeMusic(musics, idx);
        // atualiza a fila
        this.window.setQueueList(musics);
        if(playlist.size() < 2) window.setEnabledShuffleButton(false);
        if(playlist.size() == 0) window.setEnabledLoopButton(false);

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
            if(playlist.size() > 0) window.setEnabledLoopButton(true);
            if(playlist.size() > 1) window.setEnabledShuffleButton(true);
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
        // caso alguma música esteja em reprodução
        if (isPlaying) {
            stopMusic();
        }
    };

    private final ActionListener buttonListenerNext = e -> {
        // interrompe a execução atual
        threadInterrupt(playThread, bitstream, device);
        // será exibida a próxima música
        nextMusic = true;
        // se estiver pausada, a próxima inicia despausada
        if (playPause == 0) playPause = 1;
        // inicia a próxima música
        playThread = new Thread(this::playNow);
        playThread.start();
    };

    private final ActionListener buttonListenerPrevious = e -> {
        // interrompe a execução atual
        threadInterrupt(playThread, bitstream, device);
        // será exibida a música anterior
        previousMusic = true;
        // se estiver pausada, a anterior inicia despausada
        if (playPause == 0) playPause = 1;
        // inicia a música anterior
        playThread = new Thread(this::playNow);
        playThread.start();
    };

    private final ActionListener buttonListenerShuffle = e -> {
        // armazenar o estado atual da lista de reprodução
        if (!isShuffled) {
            previousPlaylist = new ArrayList<>(playlist);

            if (isPlaying) {
                Collections.swap(playlist, index, 0);
                shuffle(playlist, isPlaying);

            } else {
                shuffle(playlist, isPlaying);
            }
        } else {
            playlist = previousPlaylist;
        }

        isShuffled = !isShuffled;

        int i = 0;
        for (Song music: playlist) {
            musics[i++] = music.getDisplayInfo();
        }
        window.setQueueList(musics);    // atualizar interface
    };

    private final ActionListener buttonListenerLoop = e -> {
        loopQueue = !loopQueue;
    };

    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        private int previousState;
        @Override
        public void mouseReleased(MouseEvent e) {
            updateFrame = new Thread(() -> {
                // atualizar a barra de tempo para o tempo correto
                window.setTime((int) (currentTime * (int) currSong.getMsPerFrame()), (int) currSong.getMsLength());

                // se o proximo frame for antes do atual, precisa recomecar a musica
                if (currentTime < currentFrame) {
                    // parar a música atual e reiniciar os objetos
                    stopMusic();
                    initializeObjects();

                    // mostrar as informações no mini player
                    window.setPlayingSongInfo(currSong.getTitle(), currSong.getAlbum(), currSong.getArtist());

                    //zerar o frame, para poder skipar para o frame correto
                    currentFrame = 0;
                }

                //skipar
                try {
                    skipToFrame(currentTime);
                } catch (BitstreamException ex) {
                    throw new RuntimeException(ex);
                }

                // atualizar a interface e voltar para o estado que a música estava (pausada ou tocando)
                window.setTime((currentFrame * (int) currSong.getMsPerFrame()), (int) currSong.getMsLength());
                window.setPlayPauseButtonIcon(playPause);
                window.setEnabledPlayPauseButton(true);
                window.setEnabledStopButton(true);
                window.setEnabledPreviousButton(index != 0);
                window.setEnabledNextButton(index != playlist.size()-1);
                window.setEnabledScrubber(true);
                playPause = previousState;
            });

            updateFrame.start();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            previousState = playPause; // manter o estado em que estava após skipar os frames
            playPause = 0;  // pausar a musica
            // pegar o frame para o qual deve-se pular
            currentTime = (int) (window.getScrubberValue() / currSong.getMsPerFrame());
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            // pegar o frame para o qual deve-se pular
            currentTime = (int) (window.getScrubberValue() / currSong.getMsPerFrame());
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

    private void stopMusic(){
        // interrompe a thread
        playThread.interrupt();

        // fecha o bistream e o device
        try {
            bitstream.close();
            device.close();
        } catch (BitstreamException ex) {
            throw new RuntimeException(ex);
        }

        // reinicia a 'interface' e todos os botões
        window.setPlayPauseButtonIcon(0);
        window.setEnabledPlayPauseButton(false);
        window.setEnabledStopButton(false);
        window.setEnabledPreviousButton(false);
        window.setEnabledNextButton(false);
        window.setEnabledScrubber(false);
        isPlaying = false;
        window.resetMiniPlayer();
    }

    private static void threadInterrupt(Thread t, Bitstream b, AudioDevice d) {
        if (b != null) {
            t.interrupt();

            // garantir que a thread foi interrompida e então fechar os objetos
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
        // caso alguma música ainda esteja em execução na thread
        threadInterrupt(playThread, bitstream, device);

        playThread = new Thread(() -> {
            // setando o frame para o começo da música
            currentFrame = 0;

            // setar para a música começar no estado de play (ao invés de pause)
            playPause = 1;
            isPlaying = true;

            // selecionando a musica
            if(index == playlist.size()-1 && loopQueue) index = 0;
            else if(nextMusic) index++; // próxima música
            else if(previousMusic) index--; // música anterior
            else index = window.getIdx(); // música selecionada pelo clique do mouse
            if(index < 0) index = 0; // evita erro no index
            System.out.println(index);
            currSong = playlist.get(index);
            // reseta as variáveis next e previous
            nextMusic = false;
            previousMusic = false;

            // inicializar os objetos para reproduzir a música
            initializeObjects();

            // exibir informações da música atual
            window.setPlayingSongInfo(currSong.getTitle(), currSong.getAlbum(), currSong.getArtist());

            // tocar a musica
            while(true) {
                if (playPause == 1) {
                    try {
                        // mostrar o tempo da música e habilitar os botões
                        window.setTime((currentFrame * (int) currSong.getMsPerFrame()), (int) currSong.getMsLength());
                        window.setPlayPauseButtonIcon(playPause);
                        window.setEnabledPlayPauseButton(true);
                        window.setEnabledStopButton(true);
                        window.setEnabledPreviousButton(index != 0);
                        window.setEnabledNextButton(index != playlist.size()-1);
                        window.setEnabledScrubber(true);

                        // resetar o miniplayer e fechar os objetos quando a musica acabar
                        if (!playNextFrame()) {
                            // caso seja a última música interrompe a reprodução
                            if(index == playlist.size()-1 && !loopQueue){
                                stopMusic();
                            }
                            // caso não seja a última música, toca a próxima(semelhante à função next)
                            else{
                                playThread.interrupt();
                                nextMusic = true;
                                playThread = new Thread(this::playNow);
                                playThread.start();
                            }
                        }
                    } catch (JavaLayerException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }

        });

        playThread.start();
    }

    private void initializeObjects() {
        // inicializar os objetos, como descrito na especificação
        try {
            device = FactoryRegistry.systemRegistry().createAudioDevice();
            device.open(decoder = new Decoder());
            bitstream = new Bitstream(currSong.getBufferedInputStream());
        } catch (JavaLayerException | FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    static void shuffle(ArrayList<Song> array, boolean isPlaying) {
        Random rnd = new Random();

        if (isPlaying) {
            for (int i = array.size() - 1; i > 1; i--) {
                int idx = rnd.nextInt(1,i+1);
                Song temp = array.get(idx); // pega uma música aleatoria entre os indexes 0 e size-i e salva em temp
                array.remove(idx);          // remove essa música da playlist
                array.add(temp);            // insere essa musica no final da pl
            }

        } else {
            for (int i = array.size() - 1; i > 0; i--) {
                int idx = rnd.nextInt(i+1);
                Song temp = array.get(idx); // pega uma música aleatoria entre os indexes 0 e size-i e salva em temp
                array.remove(idx);          // remove essa música da playlist
                array.add(temp);            // insere essa musica no final da pl
            }
        }
    }
    //</editor-fold>
}