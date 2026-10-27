package app;

import com.sun.jna.NativeLibrary;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.StageStyle;

import javafx.collections.FXCollections;
import javafx.stage.Modality;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StudioControlApp extends Application {

    private static final String VLC_FOLDER_NAME = "VLC";
    private static final String CAPTURE_VIDEO_DEVICE = "USB Video";
    private static final String CAPTURE_AUDIO_DEVICE = "Digital Audio Interface (USB Digital Audio)";
    // Viền báo hiệu nút USB CAPTURE đang BẬT. Dùng inline style (thay vì
    // chỉ dựa vào CSS class) để chắc chắn luôn hiển thị được, kể cả khi
    // file CSS của app chưa (hoặc không) định nghĩa class "capture-button-active".
    private static final String CAPTURE_BUTTON_ACTIVE_STYLE =
            "-fx-border-color: #2ecc71;"
                    + "-fx-border-width: 3px;"
                    + "-fx-border-radius: 6px;"
                    + "-fx-background-radius: 6px;";
    // Viền báo hiệu nút USB CAPTURE đã KẾT NỐI nhưng chưa hiện lên
    // PROGRAM OUTPUT (trạng thái "READY" / standby, chờ bấm để BẬT).
    private static final String CAPTURE_BUTTON_READY_STYLE =
            "-fx-border-color: #f1c40f;"
                    + "-fx-border-width: 3px;"
                    + "-fx-border-radius: 6px;"
                    + "-fx-background-radius: 6px;";
    // Toạ độ dùng để đưa cửa sổ overlay USB CAPTURE ra ngoài mọi màn hình
    // vật lý (thay vì setVisible(false)) - nhờ vậy cửa sổ vẫn "sống"
    // (native peer còn nguyên) và usbCapturePlayer tiếp tục phát/giữ kết
    // nối với thiết bị USB, sẵn sàng hiện lại NGAY khi bấm BẬT, không
    // phải mở lại dialog chọn thiết bị / kết nối lại từ đầu.
    // Thời gian (ms) overlay được BẬT trong chu trình warm-up sau khi kết
    // nối xong, trước khi tự TẮT lại. Xem runUsbCaptureWarmUpCycle().
    // Danh sách chế độ {size, fps, chroma} được THỬ LẦN LƯỢT khi kết nối một
    // thiết bị video capture. Mỗi thiết bị hỗ trợ độ phân giải/định dạng
    // khác nhau (card HDMI->USB có thể xuất 4K/2K/FullHD/720p; camera tích
    // hợp thường chỉ 720p/480p), nên KHÔNG ép cứng một mode nào.
    // Thứ tự ưu tiên: FullHD (chính) -> cao hơn (2K, 4K) -> thấp hơn (720p,
    // 480p). Mỗi kích thước thử MJPG rồi YUY2. Mục cuối {null,null,null} =
    // không khai báo gì, để dshow dùng mode mặc định của thiết bị.
    // Mỗi mode chỉ được coi là thành công khi VLC THỰC SỰ có video output
    // (không dựa vào giá trị trả về của play(), vì nó luôn true).
    // Danh sách độ phân giải được chọn từ chính danh sách mà driver thiết bị
    // capture báo hỗ trợ (4096x2160 -> ... -> 800x600), chỉ giữ lại các mức
    // PHỔ BIẾN cho video/livestream (4K DCI, 4K UHD, 1440p, FullHD - mặc
    // định/ưu tiên chính, 720p). "640x480" không nằm trong danh sách driver
    // báo, nhưng vẫn được giữ làm mức MẶC ĐỊNH dự phòng cuối cùng trước
    // {null,null,null}, vì hầu hết thiết bị dshow đều chấp nhận mode này kể
    // cả khi không liệt kê tường minh.
    private static final String[][] USB_CAPTURE_MODES = {
            {"4096x2160", "30", "MJPG"},
            {"4096x2160", "30", "YUY2"},
            {"3840x2160", "30", "MJPG"},
            {"3840x2160", "30", "YUY2"},
            {"2560x1440", "30", "MJPG"},
            {"2560x1440", "30", "YUY2"},
            {"1920x1080", "30", "MJPG"},
            {"1920x1080", "30", "YUY2"},
            {"1280x720", "30", "MJPG"},
            {"1280x720", "30", "YUY2"},
            {"640x480", "30", "MJPG"},
            {"640x480", "30", "YUY2"},
            {null, null, null}
    };
    // Thời gian tối đa chờ một mode có hình trước khi thử mode kế tiếp.
    private static final int USB_CAPTURE_MODE_TIMEOUT_MS = 2500;
    private static final int USB_CAPTURE_WARMUP_SHOW_MS = 400;
    private static final int USB_CAPTURE_OFFSCREEN_X = -32000;
    private static final int USB_CAPTURE_OFFSCREEN_Y = -32000;

    private Stage primaryStage;

    /*
     * Startup splash:
     * - Không hiển thị primaryStage cho đến khi giao diện chính đã sẵn sàng.
     * - Splash luôn được hiển thị ít nhất 5 giây.
     * - VLC được khởi tạo ở background thread để JavaFX vẫn render splash,
     *   tránh hiện các khung trắng khi khởi động.
     */
    private Stage startupStage;
    private ProgressBar startupProgressBar;
    private Label startupStatusLabel;
    private static final long STARTUP_DISPLAY_TIME_MS = 5000L;
    private static final int FOUR_K_MIN_WIDTH = 3840;
    private static final int FOUR_K_MIN_HEIGHT = 2160;
    private static final int PREVIEW_RESOLUTION_CHECK_MAX_ATTEMPTS = 30;
    private Timeline previewResolutionCheckTimeline;
    private int previewResolutionCheckAttempts;

    private final ImageView previewView = new ImageView();
    private final ImageView onAirView = new ImageView();
    private Image idleOnAirImage;
    // Bản BufferedImage của cùng file ảnh idle, dùng để vẽ trực tiếp lên
    // outputCanvas (AWT) - ImageView/Image của JavaFX không dùng được ở đó.
    private BufferedImage idleOnAirNativeImage;

    private final Label previewPlaceholder = new Label("PREVIEW");
    private final Label previewSectionTitle = createSectionTitle("PREVIEW");
    private final Label visualNameLabel = new Label("No video / image selected");
    private final Label audioNameLabel = new Label("No audio selected");
    private final Label visualReadyLabel = createStateLabel("NO FILE", false);
    private final Label audioReadyLabel = createStateLabel("NO FILE", false);
    // 2 nút này đổi qua lại giữa "BROWSE" và "DELETE" tuỳ theo Preview
    // đã có file hay chưa (xem refreshVisualBrowseButton / refreshAudioBrowseButton).
    private Button browseVisualButton;
    private Button browseAudioButton;
    private Button takeButton;
    private Button selectScreenButton;
    private Button stopButton;
    private Button captureButton;

    private final Label statusLabel = new Label("READY");
    private final Label liveIndicator = new Label("● OFFLINE");

    private final Slider videoProgressSlider = new Slider();
    private final Slider audioProgressSlider = new Slider();
    private final Slider generalVolumeSlider = new Slider(0, 1, 0.80);

    private final ToggleButton autoPlayButton = new ToggleButton("AUTO PLAY: OFF");
    private boolean takingToOnAir = false;
    private boolean onAirRunning = false;

    private final ListView<File> folderListView = new ListView<>();
    private final List<File> visualSlots = new ArrayList<>();
    private final List<File> audioSlots = new ArrayList<>();

    private final Button[] visualSlotButtons = new Button[4];
    private final Button[] audioSlotButtons = new Button[5];

    // Slot (nếu có) đang được đưa lên Preview. null = nội dung Preview không đến từ Slot nào.
    private Integer activeVisualSlotIndex;
    private Integer activeAudioSlotIndex;

    private MediaPlayerFactory previewVlcFactory;
    private MediaPlayerFactory outputVlcFactory;
    private EmbeddedMediaPlayer previewPlayer;
    private EmbeddedMediaPlayer onAirPlayer;
    private MediaPlayer externalAudioPlayer;

    /*
     * USB CAPTURE hoạt động như một lớp "đè hình" (overlay) độc lập, hoàn
     * toàn tách biệt với onAirPlayer:
     * - onAirPlayer/outputCanvas không hề bị stop/pause/đổi gì cả khi bật
     *   USB CAPTURE, nên video/ảnh đang ON AIR (kể cả audio, AUTO PLAY...)
     *   vẫn chạy bình thường phía dưới.
     * - usbCapturePlayer render lên MỘT Canvas/JFrame native riêng, luôn
     *   always-on-top và được đặt đúng vị trí/kích thước của
     *   nativeOutputFrame -> về mặt hình ảnh, nó "đè" lên PROGRAM OUTPUT.
     * - Chỉ mở video (":dshow-adev=none"), không lấy audio từ thiết bị USB.
     * - MediaPlayerFactory riêng (usbCaptureVlcFactory) được tạo với
     *   "--no-audio" như một lớp bảo vệ thứ hai, đảm bảo player này không
     *   bao giờ có audio output dù media/tuỳ chọn có thay đổi thế nào.
     */
    private MediaPlayerFactory usbCaptureVlcFactory;
    private EmbeddedMediaPlayer usbCapturePlayer;
    private JFrame usbCaptureOverlayFrame;
    private Canvas usbCaptureCanvas;
    // usbCaptureConnected = đã chọn thiết bị và usbCapturePlayer đang PHÁT
    // (device đã mở), nhưng CHƯA CHẮC đang hiện trên PROGRAM OUTPUT.
    // usbCaptureOverlayActive = overlay hiện đang được HIỂN THỊ (đè lên)
    // trên PROGRAM OUTPUT. Overlay chỉ có thể active khi đã connected.
    // Tách 2 trạng thái này để BẬT/TẮT chỉ là ẩn/hiện (nhanh, không đụng
    // tới kết nối thiết bị), còn việc kết nối/ngắt kết nối thiết bị USB
    // là một hành động riêng (chọn thiết bị lần đầu / đổi màn hình xuất /
    // đóng app).
    private boolean usbCaptureConnected;
    private boolean usbCaptureOverlayActive;
    // usbCaptureStreamReady = usbCapturePlayer đã THỰC SỰ có video output
    // (đã nhận frame đầu tiên từ thiết bị). media().play() trả về ngay
    // nhưng dshow cần ~0.5-2s mới lên hình; bật overlay trước lúc đó sẽ
    // thấy đen/giật -> đó là lý do "phải bật lần 2 mới ổn định".
    // usbCaptureShowPending = người dùng đã bấm BẬT khi stream chưa sẵn
    // sàng -> tự động hiện overlay ngay khi stream sẵn sàng.
    private volatile boolean usbCaptureStreamReady;
    private volatile boolean usbCaptureShowPending;
    // Đang chạy chu trình "bật rồi tắt ngay" sau khi kết nối (xem
    // runUsbCaptureWarmUpCycle). Tăng usbCaptureGeneration mỗi lần kết nối
    // mới để các thread/timer của lần kết nối cũ tự huỷ.
    private volatile boolean usbCaptureWarmingUp;
    private volatile int usbCaptureGeneration;
    // Trạng thái của MỘT lần thử mode (xem openUsbCaptureWithBestMode).
    private volatile boolean usbCaptureAttemptVideoSeen;
    private volatile boolean usbCaptureAttemptFailed;
    // Mô tả mode đang chạy, vd "1920x1080 MJPG 30fps" (chỉ để hiển thị).
    private volatile String usbCaptureActiveModeLabel = "";
    // Tên thiết bị (friendly name) người dùng đã chọn ở dialog chọn
    // thiết bị USB CAPTURE gần nhất - dùng lại làm giá trị mặc định
    // trong lần mở dialog kế tiếp, và để hiển thị trên nút/status bar.
    private String selectedUsbCaptureDeviceName;

    private File selectedVisualFile;
    private File selectedAudioFile;
    private boolean previewIsImage;
    private boolean onAirIsImage;
    private boolean closing;
    // true kể từ lần đầu tiên tự khởi động lại app do mất tín hiệu
    // SCREEN/USB CAPTURE - chặn kích hoạt restart nhiều lần cùng lúc.
    private boolean isRestarting;

    /*
     * WATCHDOG kiểm tra Device Manager định kỳ cho USB CAPTURE.
     *
     * LÝ DO CẦN THÊM (ngoài handlePossibleUsbCaptureSignalLost dựa vào
     * error()/stopped()/finished() của usbCapturePlayer): với thiết bị
     * dshow, khi rút cáp USB thật sự hoặc DISABLE thiết bị trong Windows
     * Device Manager, driver rất hay KHÔNG bắn lỗi cho LibVLC - stream
     * chỉ đứng hình (đứng ở frame cuối) mà player vẫn báo isPlaying() =
     * true, nên handlePossibleUsbCaptureSignalLost() không được gọi và
     * app không tự khởi động lại như yêu cầu. Đây chính là lỗi khi test
     * bằng cách disable/unplug rồi kiểm tra trong Device Manager.
     *
     * Cách vá: định kỳ dò lại danh sách "Cameras" bằng đúng lệnh
     * PowerShell "Get-PnpDevice" đã dùng ở listVideoCaptureDevices() (mục
     * Device Manager > Cameras) và so với tên thiết bị đang dùng
     * (selectedUsbCaptureDeviceName). Nếu thiết bị không còn trong danh
     * sách "PresentOnly" nữa (đã rút / đã bị disable) trong khi
     * usbCaptureConnected vẫn true -> coi như mất tín hiệu thật sự và
     * restartApplication() giống hệt 2 đường phát hiện còn lại.
     */
    private static final long USB_CAPTURE_DEVICE_WATCHDOG_INTERVAL_MS = 3000L;
    private Timeline usbCaptureDeviceWatchdogTimeline;
    private volatile boolean usbCaptureDeviceCheckInFlight;


    /*
     * PROGRAM OUTPUT (màn hình phát ra thực tế) giờ được render bằng
     * một java.awt.Canvas native, gắn thẳng vào onAirPlayer qua
     * MediaPlayerFactory.videoSurfaces().newVideoSurface(canvas).
     *
     * Đây là điểm mấu chốt để dùng GPU thật sự: LibVLC được cấp thẳng
     * window handle của Canvas và tự vẽ (Direct3D9/Direct3D11 trên
     * Windows) vào đó, không phải copy từng frame buffer qua CPU rồi
     * upload lại thành texture như ImageViewVideoSurface (JavaFX) vẫn
     * làm - đó chính là nguyên nhân CPU bị dùng nặng ở video 4K/8K.
     *
     * Canvas/JFrame này được tạo MỘT LẦN duy nhất khi app khởi động và
     * tồn tại suốt vòng đời app; chọn màn hình chỉ là ẩn/hiện + đổi vị
     * trí, không tạo lại video surface.
     */
    private JFrame nativeOutputFrame;
    private OutputCanvas outputCanvas;
    private boolean outputScreenOpen;
    private Screen selectedOutputScreen;
    private Rectangle selectedOutputScreenAwtBounds;

    /*
     * Vì PROGRAM OUTPUT giờ render trực tiếp bằng Canvas (không còn đi
     * qua onAirView nữa), khung "PROGRAM OUTPUT" nhỏ trong Studio Center
     * không thể tiếp tục nhận video full frame-rate từ cùng một
     * VideoSurface (một MediaPlayer chỉ có 1 video surface tại một thời
     * điểm). Thay vào đó, khung này hiển thị ảnh snapshot được làm mới
     * mỗi giây - gần như không tốn CPU (so với 30-60 lần copy/giây của
     * cách cũ) nhưng vẫn cho người vận hành thấy nội dung đang ON AIR.
     */
    private Timeline onAirThumbnailTimeline;

    // Tham chiếu tới 2 khung hiển thị để đồng bộ chiều cao (viền đỏ = viền xanh lá).
    private StackPane onAirDisplayPane;
    private StackPane previewDisplayPane;


    @Override
    public void start(Stage stage) {
        primaryStage = stage;


        /*
         * QUAN TRỌNG:
         * Không khởi tạo VLC và không show cửa sổ chính ngay tại đây.
         *
         * VLCJ/LibVLC có thể mất thời gian load DLL + plugins.
         * Nếu làm việc đó trên JavaFX Application Thread, cửa sổ có thể
         * xuất hiện vài frame trắng trước khi giao diện ổn định.
         */
        showStartupBackground();

        final long startupStart = System.nanoTime();

        Thread startupThread = new Thread(() -> {
            try {
                /*
                 * Khởi tạo VLC ngoài JavaFX Application Thread.
                 * attachVideoSurfaces() sẽ được gọi sau, trên FX thread,
                 * vì nó gắn VLC vào ImageView của JavaFX.
                 */
                initializeVlc();

                long elapsedMs =
                        (System.nanoTime() - startupStart) / 1_000_000L;

                long remainingMs =
                        Math.max(
                                0L,
                                STARTUP_DISPLAY_TIME_MS - elapsedMs
                        );

                Platform.runLater(() ->
                        finishStartupAfterDelay(remainingMs)
                );

            } catch (Exception exception) {
                Platform.runLater(() -> {
                    closeStartupBackground();

                    showError(
                            "Không thể khởi tạo VLC",
                            exception.getMessage()
                    );

                    Platform.exit();
                });
            }
        }, "DisplayFX-Startup");

        startupThread.setDaemon(true);
        startupThread.start();
    }

    /**
     * Hiển thị splash/background trong lúc ứng dụng khởi tạo.
     *
     * Thứ tự ưu tiên:
     * 1. /images/startup-background.png
     * 2. /images/on-air-idle.png
     * 3. nền đen nếu không có ảnh.
     *
     * Splash dùng StageStyle.UNDECORATED nên không có title bar trắng
     * của cửa sổ Windows.
     */
    private void showStartupBackground() {
        startupStage = new Stage(StageStyle.UNDECORATED);
        startupStage.setAlwaysOnTop(true);
        startupStage.setResizable(false);
        startupStage.setWidth(720);
        startupStage.setHeight(405);

        startupStage.getIcons().add(
                new Image(getClass().getResourceAsStream("/images/icon.png"))
        );

        StackPane root = new StackPane();
        root.setStyle(
                "-fx-background-color: #050505;"
        );

        ImageView backgroundView = new ImageView();
        backgroundView.setPreserveRatio(false);
        backgroundView.setSmooth(true);
        backgroundView.setMouseTransparent(true);

        Image backgroundImage = loadStartupBackground();

        if (backgroundImage != null && !backgroundImage.isError()) {
            backgroundView.setImage(backgroundImage);

            backgroundView.fitWidthProperty()
                    .bind(root.widthProperty());

            backgroundView.fitHeightProperty()
                    .bind(root.heightProperty());

            root.getChildren().add(backgroundView);
        }

        // Lớp phủ tối
        Region darkOverlay = new Region();
        darkOverlay.setStyle(
                "-fx-background-color: rgba(0,0,0,0.55);"
        );

        // ==============================
        // STARTUP CONTENT
        // ==============================

        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(300);
        content.setPrefWidth(300);

        startupStatusLabel = new Label(
                "LOADING MEDIA ENGINE..."
        );

        startupStatusLabel.setAlignment(Pos.CENTER);
        startupStatusLabel.setMaxWidth(Double.MAX_VALUE);

        startupStatusLabel.setStyle(
                "-fx-text-fill: #d8dde2;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-family: 'Segoe UI';"
        );

        // Thanh loading
        startupProgressBar = new ProgressBar(0);

        startupProgressBar.setPrefWidth(260);
        startupProgressBar.setMinWidth(260);
        startupProgressBar.setMaxWidth(260);

        startupProgressBar.setPrefHeight(5);

        // Gom status + progress vào giữa
        content.getChildren().addAll(
                startupStatusLabel,
                startupProgressBar
        );

        /*
         * Đặt toàn bộ cụm loading
         * ở chính giữa màn hình.
         */
        StackPane.setAlignment(
                content,
                Pos.CENTER
        );

        /*
         * Có thể điều chỉnh vị trí theo chiều dọc
         * nếu muốn cụm loading thấp hơn một chút.
         */
        StackPane.setMargin(
                content,
                new Insets(100, 0, 0, 0)
        );

        root.getChildren().addAll(
                darkOverlay,
                content
        );

        Scene splashScene = new Scene(
                root,
                720,
                405,
                Color.BLACK
        );

        startupStage.setScene(splashScene);

        // ==============================
        // CENTER SPLASH
        // ==============================

        Rectangle2D bounds =
                Screen.getPrimary().getVisualBounds();

        startupStage.setX(
                bounds.getMinX()
                        + (bounds.getWidth() - 720) / 2
        );

        startupStage.setY(
                bounds.getMinY()
                        + (bounds.getHeight() - 405) / 2
        );

        startupStage.show();
        startupStage.toFront();

        // ==============================
        // LOADING ANIMATION - 5 SECONDS
        // ==============================

        Timeline progressTimeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(
                                startupProgressBar.progressProperty(),
                                0
                        )
                ),
                new KeyFrame(
                        Duration.millis(
                                STARTUP_DISPLAY_TIME_MS
                        ),
                        new KeyValue(
                                startupProgressBar.progressProperty(),
                                1
                        )
                )
        );

        progressTimeline.play();
    }

    /**
     * Tải background splash.
     * Có thể thêm file:
     *
     * /images/startup-background.png
     *
     * Nếu chưa có, chương trình tự dùng on-air-idle.png.
     */
    private Image loadStartupBackground() {
        String[] fileNames = {
                "on-air-idle.png",
                "startup-background.png"

        };

        for (String fileName : fileNames) {
            Image image = loadAppImage(fileName);

            if (image != null) {
                return image;
            }
        }

        return null;
    }

    /**
     * Tải một ảnh dùng trong app (on-air-idle.png, startup-background.png,...).
     *
     * Thử theo thứ tự:
     * 1. Resource trong classpath: /images/&lt;tên file&gt;
     *    (ảnh được nhúng sẵn trong jar lúc build).
     * 2. File bên ngoài: &lt;thư mục chạy app&gt;/images/&lt;tên file&gt;
     *    - cùng kiểu với cách app tìm thư mục VLC bằng
     *    getAppDirectory() - để có thể đổi ảnh
     *    mà KHÔNG cần build lại jar.
     *
     * Nếu không tìm thấy ở cả 2 nơi (hoặc ảnh lỗi), in ra console đường dẫn
     * đã thử, để dễ chẩn đoán khi ảnh "không hiển thị".
     */
    private Image loadAppImage(String fileName) {
        String classpathResource = "/images/" + fileName;
        var resourceUrl = getClass().getResource(classpathResource);

        if (resourceUrl != null) {
            Image image = new Image(resourceUrl.toExternalForm());

            if (!image.isError()) {
                return image;
            }

            System.err.println(
                    "Loi khi doc anh tu resource "
                            + classpathResource
                            + ": "
                            + image.getException()
            );
        }

        File externalFile = new File(
                new File(
                        getAppDirectory(),
                        "images"
                ),
                fileName
        );

        if (externalFile.isFile()) {
            Image image = new Image(externalFile.toURI().toString());

            if (!image.isError()) {
                return image;
            }

            System.err.println(
                    "Loi khi doc anh tu file "
                            + externalFile.getAbsolutePath()
                            + ": "
                            + image.getException()
            );
        }

        System.err.println(
                "Khong tim thay anh \""
                        + fileName
                        + "\". Da thu:\n"
                        + "  - classpath resource: "
                        + classpathResource
                        + "\n"
                        + "  - file ben ngoai:      "
                        + externalFile.getAbsolutePath()
        );

        return null;
    }

    /**
     * Giống {@link #loadAppImage(String)} nhưng trả về BufferedImage (AWT)
     * thay vì Image (JavaFX), để có thể vẽ trực tiếp lên {@link OutputCanvas}
     * - tức là hiển thị lên MÀN HÌNH ĐANG TRÌNH CHIẾU (PROGRAM OUTPUT),
     * chứ không chỉ khung ON AIR nhỏ trong Studio Center.
     *
     * Thử đúng 2 nơi như loadAppImage: resource trong classpath rồi tới
     * file bên ngoài <thư mục chạy app>/images/&lt;tên file&gt;.
     */
    private BufferedImage loadAppBufferedImage(String fileName) {
        String classpathResource = "/images/" + fileName;

        try (var inputStream =
                     getClass().getResourceAsStream(classpathResource)) {

            if (inputStream != null) {
                BufferedImage image = ImageIO.read(inputStream);

                if (image != null) {
                    return image;
                }
            }
        } catch (Exception exception) {
            System.err.println(
                    "Loi khi doc anh (BufferedImage) tu resource "
                            + classpathResource
                            + ": "
                            + exception.getMessage()
            );
        }

        File externalFile = new File(
                new File(
                        getAppDirectory(),
                        "images"
                ),
                fileName
        );

        if (externalFile.isFile()) {
            try {
                BufferedImage image = ImageIO.read(externalFile);

                if (image != null) {
                    return image;
                }
            } catch (Exception exception) {
                System.err.println(
                        "Loi khi doc anh (BufferedImage) tu file "
                                + externalFile.getAbsolutePath()
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        return null;
    }

    /**
     * Đảm bảo splash tồn tại tối thiểu 5 giây.
     * Nếu VLC khởi tạo xong sớm, chờ phần thời gian còn lại.
     */
    private void finishStartupAfterDelay(long remainingMs) {
        if (closing) {
            return;
        }

        if (remainingMs <= 0) {
            finishStartup();
            return;
        }

        if (startupStatusLabel != null) {
            startupStatusLabel.setText(
                    "PREPARING USER INTERFACE..."
            );
        }

        Timeline delay = new Timeline(
                new KeyFrame(
                        Duration.millis(remainingMs)
                )
        );

        delay.setOnFinished(event ->
                finishStartup()
        );

        delay.play();
    }

    /**
     * Tạo giao diện chính sau khi splash kết thúc.
     * Chỉ tại đây mới attach VLC vào JavaFX ImageView.
     */
    private void finishStartup() {
        if (closing || primaryStage == null) {
            return;
        }

        try {
            initializeState();
            attachVideoSurfaces();
            configureVlcEvents();
            configureProgressSliders();
            configureScreenConnectionMonitoring();
            configureUsbCaptureDeviceWatchdog();

            BorderPane root = createRootLayout();

            Rectangle2D visualBounds =
                    Screen.getPrimary()
                            .getVisualBounds();

            Scene scene = new Scene(
                    root,
                    visualBounds.getWidth() * 0.9,
                    visualBounds.getHeight() * 0.9
            );

            applyApplicationStyle(scene);

            scene.addEventFilter(
                    KeyEvent.KEY_PRESSED,
                    this::handleKeyboardShortcut
            );

            primaryStage.setTitle(
                    "Studio Control Center - NgoDinh Entertainment"
            );
            primaryStage.getIcons().add(
                    new Image(getClass().getResourceAsStream("/images/icon.png"))
            );

            primaryStage.setScene(scene);

            /*
             * Không đặt minWidth/minHeight quá lớn.
             * Giữ nguyên logic scale hiện tại của ứng dụng.
             */
            primaryStage.setMinWidth(
                    Math.min(
                            900,
                            visualBounds.getWidth() * 0.75
                    )
            );

            primaryStage.setMinHeight(
                    Math.min(
                            600,
                            visualBounds.getHeight() * 0.75
                    )
            );

            primaryStage.setOnCloseRequest(event -> {
                closing = true;
                closeOutputScreen();
                disposeAllPlayers();
            });

            /*
             * Quan trọng:
             * Đóng splash trước, sau đó mới show primaryStage.
             *
             * Vì Scene + CSS + layout đã được tạo hoàn chỉnh trước khi
             * primaryStage.show(), người dùng không thấy cửa sổ trắng
             * trong quá trình dựng giao diện.
             */
            closeStartupBackground();

            primaryStage.show();

            Platform.runLater(() -> {
                if (primaryStage == null
                        || !primaryStage.isShowing()) {
                    return;
                }

                primaryStage.setMaximized(true);

                if (primaryStage.getScene() != null) {
                    primaryStage.getScene()
                            .getRoot()
                            .applyCss();

                    primaryStage.getScene()
                            .getRoot()
                            .requestLayout();
                }
            });

            /*
             * JavaFX trên Windows có lỗi khá phổ biến: khi kéo Stage từ
             * màn hình này sang màn hình khác có tỉ lệ scale (DPI) khác
             * nhau, chữ/nút hoặc 2 khung Preview/ON AIR không được vẽ lại
             * đúng theo scale mới ngay lập tức. Watchdog này tự phát hiện
             * và ép vẽ lại, giống cách các ứng dụng Windows gốc xử lý.
             */
            installDpiChangeWatchdog();

        } catch (Exception exception) {
            closeStartupBackground();

            showError(
                    "Không thể khởi động giao diện",
                    exception.getMessage()
            );

            Platform.exit();
        }
    }

    private void closeStartupBackground() {
        if (startupStage == null) {
            return;
        }

        Stage stage = startupStage;
        startupStage = null;

        if (stage.isShowing()) {
            stage.close();
        }
    }

    private BorderPane createRootLayout() {
        BorderPane root = new BorderPane();

        root.setMinWidth(0);
        root.setMinHeight(0);

        root.getStyleClass().add(
                "root-panel"
        );

        root.setTop(
                createTopBar()
        );

        root.setCenter(
                createMainContent()
        );

        root.setBottom(
                createStatusBar()
        );

        return root;
    }

    private void applyApplicationStyle(
            Scene scene
    ) {
        var cssUrl = getClass().getResource(
                "/css/studio-theme.css"
        );

        if (cssUrl == null) {
            System.err.println(
                    "Không tìm thấy CSS: "
                            + "/css/studio-theme.css"
            );

            return;
        }

        scene.getStylesheets().add(
                cssUrl.toExternalForm()
        );
    }

    /**
     * Thư mục cài đặt thật của app (thư mục chứa DisplayFX.exe, app, runtime, VLC).
     *
     * KHÔNG dùng System.getProperty("user.dir"): khi app chạy từ MS Store (MSIX),
     * hoặc mở bằng shortcut/Start Menu/file association, working directory
     * thường là C:\\Windows\\System32 chứ không phải thư mục cài đặt -> báo
     * "không tìm thấy VLC tại system32".
     *
     * Thứ tự thử (chọn thư mục đầu tiên có chứa thư mục VLC hợp lệ):
     *  1. jpackage.app-path  (jpackage tự set = đường dẫn tới DisplayFX.exe)
     *  2. java.home          (…\\DisplayFX\\runtime  -> thư mục cha)
     *  3. Đường dẫn của tiến trình hiện tại (ProcessHandle)
     *  4. Vị trí file jar/class của app (…\\DisplayFX\\app -> thư mục cha)
     *  5. user.dir (dự phòng, dùng khi chạy từ IDE/java -jar)
     */
    private static volatile File cachedAppDirectory;

    private File getAppDirectory() {
        File cached = cachedAppDirectory;
        if (cached != null) {
            return cached;
        }

        List<File> candidates = new ArrayList<>();

        // 1. exe do jpackage tạo ra
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            addParentCandidate(candidates, new File(appPath), 1);
        }

        // 2. <app>/runtime là java.home của bản jpackage
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            addParentCandidate(candidates, new File(javaHome), 1);
        }

        // 3. đường dẫn exe của tiến trình đang chạy
        try {
            ProcessHandle.current().info().command().ifPresent(
                    cmd -> addParentCandidate(candidates, new File(cmd), 1)
            );
        } catch (Exception ignored) {
            // bỏ qua, thử cách khác
        }

        // 4. vị trí jar / thư mục class của app
        try {
            File codeSource = new File(
                    StudioControlApp.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            // …\\app\\StudioControlApp.jar -> …\\app -> …
            addParentCandidate(candidates, codeSource, 2);
        } catch (Exception ignored) {
            // bỏ qua, thử cách khác
        }

        // 5. dự phòng
        candidates.add(new File(System.getProperty("user.dir")));

        for (File candidate : candidates) {
            if (isValidVlcDirectory(new File(candidate, VLC_FOLDER_NAME))) {
                cachedAppDirectory = candidate;
                return candidate;
            }
        }

        // Không tìm thấy VLC ở đâu cả: trả về ứng viên ưu tiên nhất để
        // thông báo lỗi hiển thị đúng đường dẫn đã thử.
        File fallback = candidates.get(0);
        System.err.println("Khong tim thay thu muc VLC. Da thu cac thu muc:");
        for (File candidate : candidates) {
            System.err.println("  - " + candidate.getAbsolutePath());
        }
        return fallback;
    }

    /** Thêm thư mục cha (lên {@code levels} cấp) của file/thư mục vào danh sách. */
    private static void addParentCandidate(List<File> candidates,
                                           File file, int levels) {
        File current = file;
        for (int i = 0; i < levels && current != null; i++) {
            current = current.getParentFile();
        }
        if (current != null && !candidates.contains(current)) {
            candidates.add(current);
        }
        // jar nằm ngay trong <app>\app => thử thêm cả 1 cấp gần hơn
        if (levels > 1 && file.getParentFile() != null
                && !candidates.contains(file.getParentFile())) {
            candidates.add(file.getParentFile());
        }
    }

    private void initializeVlc() {
        File vlcDirectory = new File(
                getAppDirectory(),
                VLC_FOLDER_NAME
        );

        validateVlcDirectory(vlcDirectory);

        String vlcPath = vlcDirectory.getAbsolutePath();
        String pluginPath = new File(
                vlcDirectory,
                "plugins"
        ).getAbsolutePath();

        configureNativeVlcPath(vlcPath);
        createVlcFactories(pluginPath);
        createVlcPlayers();
    }

    private void validateVlcDirectory(File vlcDirectory) {
        if (isValidVlcDirectory(vlcDirectory)) {
            return;
        }

        throw new IllegalStateException(
                "Không tìm thấy thư mục VLC tại:\n"
                        + vlcDirectory.getAbsolutePath()
                        + "\n\nThư mục phải chứa:"
                        + "\n- libvlc.dll"
                        + "\n- libvlccore.dll"
                        + "\n- plugins"
        );
    }

    private void configureNativeVlcPath(String vlcPath) {
        System.setProperty("jna.library.path", vlcPath);
        NativeLibrary.addSearchPath("libvlc", vlcPath);
        NativeLibrary.addSearchPath("libvlccore", vlcPath);
    }

    private void createVlcFactories(String pluginPath) {
        /*
         * Preview dùng một LibVLC instance riêng và không tạo audio output.
         * Không gọi mute hoặc volume = 0 trên Preview vì Windows có thể
         * ghi nhớ trạng thái đó cho toàn bộ phiên âm thanh của ứng dụng Java.
         */
        previewVlcFactory = new MediaPlayerFactory(
                "--no-audio",
                "--no-video-title-show",
                "--no-osd",
                "--quiet",
                "--plugin-path=" + pluginPath
        );

        /*
         * Output dùng LibVLC instance riêng, có âm thanh bình thường.
         *
         * --no-osd BẮT BUỘC phải có ở đây: onAirPlayer vừa là player
         * phát ra PROGRAM OUTPUT (Canvas GPU) vừa là player được chụp
         * snapshot mỗi giây cho khung preview trong app (xem
         * requestOnAirThumbnailSnapshot). Nếu không tắt OSD, mỗi lần
         * chụp snapshot VLC sẽ tự vẽ đè lên chính màn hình phát sóng
         * một dòng chữ đường dẫn file + icon thumbnail nhỏ - đó chính
         * là ô nhỏ và dòng chữ bị "cháy" lên màn hình Output.
         */
        outputVlcFactory = new MediaPlayerFactory(
                "--no-video-title-show",
                "--no-osd",
                "--quiet",
                "--plugin-path=" + pluginPath
        );

        /*
         * USB CAPTURE dùng LibVLC instance riêng, "--no-audio" ở mức
         * instance để CHẮC CHẮN không bao giờ tạo audio output cho lớp
         * đè hình này, dù tuỳ chọn media có vô tình bật audio hay không.
         */
        usbCaptureVlcFactory = new MediaPlayerFactory(
                "--no-audio",
                "--no-video-title-show",
                "--no-osd",
                "--quiet",
                "--plugin-path=" + pluginPath
        );
    }

    private void createVlcPlayers() {
        previewPlayer = previewVlcFactory
                .mediaPlayers()
                .newEmbeddedMediaPlayer();

        onAirPlayer = outputVlcFactory
                .mediaPlayers()
                .newEmbeddedMediaPlayer();

        usbCapturePlayer = usbCaptureVlcFactory
                .mediaPlayers()
                .newEmbeddedMediaPlayer();
    }

    private void attachVideoSurfaces() {
        /*
         * Preview vẫn dùng ImageViewVideoSurface (JavaFX callback).
         * Preview thường nhỏ, không phải nội dung phát ra thật, nên
         * chấp nhận chi phí CPU này để giữ nguyên UI hiện có.
         */
        previewPlayer.videoSurface().set(
                new ImageViewVideoSurface(previewView)
        );

        /*
         * ON AIR / PROGRAM OUTPUT dùng Canvas native để LibVLC render
         * bằng GPU trực tiếp - xem giải thích chi tiết ở khai báo field
         * nativeOutputFrame/outputCanvas.
         */
        createNativeOutputWindow();

        /*
         * USB CAPTURE overlay - Canvas/JFrame riêng, tạo MỘT LẦN và gắn
         * video surface cố định giống hệt cách làm với PROGRAM OUTPUT ở
         * trên (createNativeOutputWindow), chỉ khác là cửa sổ này ẩn cho
         * tới khi người dùng bấm nút USB CAPTURE.
         */
        createUsbCaptureOverlayWindow();
    }

    /**
     * Tạo (một lần) cửa sổ AWT/Swing chứa Canvas dùng làm bề mặt render
     * GPU cho onAirPlayer. Cửa sổ này ẩn cho tới khi người dùng chọn
     * màn hình xuất (SELECT SCREEN) qua {@link #openOutputScreen}.
     */
    private void createNativeOutputWindow() {
        outputCanvas = new OutputCanvas();
        outputCanvas.setBackground(java.awt.Color.BLACK);
        /*
         * LibVLC tự vẽ trực tiếp lên Canvas này (Direct3D9/Direct3D11),
         * không cần và không nên để AWT tự repaint/erase nền.
         */
        outputCanvas.setIgnoreRepaint(true);

        nativeOutputFrame = new JFrame();
        nativeOutputFrame.setUndecorated(true);
        nativeOutputFrame.setBackground(java.awt.Color.BLACK);
        nativeOutputFrame.setLayout(new BorderLayout());
        nativeOutputFrame.getContentPane().setBackground(java.awt.Color.BLACK);
        nativeOutputFrame.add(outputCanvas, BorderLayout.CENTER);
        nativeOutputFrame.setResizable(false);
        nativeOutputFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        /*
         * nativeOutputFrame là JFrame (AWT/Swing), không phải JavaFX Stage,
         * nên phải dùng setIconImage(java.awt.Image) thay vì getIcons().
         * Cửa sổ undecorated nên icon sẽ không hiện trên title bar, nhưng
         * vẫn có thể được Windows dùng làm icon taskbar/Alt+Tab.
         */
        try {
            java.awt.Image outputIcon = java.awt.Toolkit.getDefaultToolkit()
                    .getImage(getClass().getResource("/images/icon.png"));
            nativeOutputFrame.setIconImage(outputIcon);
        } catch (Exception e) {
            System.err.println(
                    "Không thể set icon cho PROGRAM OUTPUT window: " + e.getMessage()
            );
        }

        nativeOutputFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                /*
                 * Cửa sổ undecorated + always-on-top nên gần như không
                 * bao giờ nhận sự kiện này qua thao tác chuột, nhưng vẫn
                 * xử lý cho các trường hợp đóng bằng phím tắt hệ điều
                 * hành (vd Alt+F4).
                 */
                Platform.runLater(() -> {
                    closeOutputScreen();
                    setStatus("PROGRAM OUTPUT closed");
                });
            }

            @Override
            public void windowIconified(WindowEvent event) {
                SwingUtilities.invokeLater(() -> {
                    if (!outputScreenOpen) {
                        return;
                    }

                    nativeOutputFrame.setExtendedState(java.awt.Frame.NORMAL);
                    nativeOutputFrame.setAlwaysOnTop(true);
                    nativeOutputFrame.toFront();
                });
            }
        });

        /*
         * QUAN TRỌNG: gắn video surface NGAY và CỐ ĐỊNH trong suốt vòng
         * đời app - đây chính là bước bật GPU rendering cho ON AIR.
         */
        VideoSurface nativeVideoSurface =
                outputVlcFactory.videoSurfaces().newVideoSurface(outputCanvas);

        onAirPlayer.videoSurface().set(nativeVideoSurface);

        /*
         * outputCanvas vừa được tạo xong ở đây (trước đó null lúc
         * initializeState() gọi showIdleOnAirImage() lần đầu).
         * Vẽ ngay ảnh idle lên Canvas để PROGRAM OUTPUT có sẵn ảnh
         * idle chờ, kể cả trước khi người dùng SELECT SCREEN.
         */
        if (!onAirRunning) {
            showIdleImageOnNativeOutput();
        }
    }

    /**
     * Tạo (một lần) cửa sổ AWT/Swing riêng dùng làm lớp "đè hình" cho
     * USB CAPTURE. Cửa sổ này luôn undecorated, ẩn/ngoài màn hình cho
     * tới khi {@link #connectUsbCapture(String)} được gọi (kết nối, vẫn
     * chưa hiện), rồi được {@link #showUsbCaptureOverlay()} đặt đúng vị
     * trí/kích thước của {@link #nativeOutputFrame} và always-on-top để
     * đè lên đúng vùng đang phát PROGRAM OUTPUT mà không đụng vào
     * onAirPlayer/outputCanvas.
     */
    private void createUsbCaptureOverlayWindow() {
        usbCaptureCanvas = new Canvas();
        usbCaptureCanvas.setBackground(java.awt.Color.BLACK);
        usbCaptureCanvas.setIgnoreRepaint(true);

        usbCaptureOverlayFrame = new JFrame();
        usbCaptureOverlayFrame.setUndecorated(true);
        usbCaptureOverlayFrame.setBackground(java.awt.Color.BLACK);
        usbCaptureOverlayFrame.setLayout(new BorderLayout());
        usbCaptureOverlayFrame.getContentPane().setBackground(java.awt.Color.BLACK);
        usbCaptureOverlayFrame.add(usbCaptureCanvas, BorderLayout.CENTER);
        usbCaptureOverlayFrame.setResizable(false);
        usbCaptureOverlayFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        // Không cho overlay cướp focus bàn phím của Studio Center.
        usbCaptureOverlayFrame.setFocusableWindowState(false);

        /*
         * QUAN TRỌNG: gắn video surface NGAY và CỐ ĐỊNH trong suốt vòng
         * đời app, giống hệt onAirPlayer ở createNativeOutputWindow().
         */
        VideoSurface usbCaptureVideoSurface =
                usbCaptureVlcFactory.videoSurfaces().newVideoSurface(usbCaptureCanvas);

        usbCapturePlayer.videoSurface().set(usbCaptureVideoSurface);

        usbCapturePlayer.events().addMediaPlayerEventListener(
                new MediaPlayerEventAdapter() {
                    @Override
                    public void videoOutput(MediaPlayer mediaPlayer, int newCount) {
                        if (newCount > 0) {
                            usbCaptureAttemptVideoSeen = true;
                            markUsbCaptureStreamReady(usbCaptureGeneration);
                        }
                    }

                    /*
                     * Thiết bị không mở được với mode đang thử (dshow lỗi /
                     * player tự dừng) -> báo cho openUsbCaptureWithBestMode
                     * để thử mode kế tiếp ngay, không phải chờ hết timeout.
                     * Việc "sẵn sàng khi driver không phát videoOutput" cũng
                     * do openUsbCaptureWithBestMode xử lý (thay cho fallback
                     * 2.5s cũ - fallback đó khiến app báo READY dù chưa có
                     * hình, không hề báo lỗi).
                     */
                    @Override
                    public void error(MediaPlayer mediaPlayer) {
                        usbCaptureAttemptFailed = true;
                        handlePossibleUsbCaptureSignalLost();
                    }

                    @Override
                    public void stopped(MediaPlayer mediaPlayer) {
                        usbCaptureAttemptFailed = true;
                        handlePossibleUsbCaptureSignalLost();
                    }

                    @Override
                    public void finished(MediaPlayer mediaPlayer) {
                        usbCaptureAttemptFailed = true;
                        handlePossibleUsbCaptureSignalLost();
                    }
                }
        );
    }

    /**
     * usbCapturePlayer bắn error()/stopped()/finished() ở 2 tình huống khác
     * nhau: (1) đang DÒ mode kết nối trong openUsbCaptureWithBestMode() -
     * usbCaptureAttemptFailed đã lo việc này, không cần làm gì thêm ở đây;
     * (2) thiết bị ĐÃ kết nối ổn định (usbCaptureStreamReady) rồi ĐỘT NGỘT
     * mất tín hiệu (rút cáp USB, driver crash...) - chỉ trường hợp này mới
     * coi là MẤT TÍN HIỆU thật sự khi đang chạy, và theo yêu cầu, phản ứng
     * bằng cách tự khởi động lại toàn bộ app ngay lập tức.
     */
    private void handlePossibleUsbCaptureSignalLost() {
        if (!usbCaptureConnected || !usbCaptureStreamReady) {
            return;
        }
        Platform.runLater(() ->
                restartApplication("USB CAPTURE signal was lost unexpectedly")
        );
    }

    /**
     * Được gọi (từ thread của VLC) khi usbCapturePlayer đã có hình.
     * Chờ thêm một nhịp ngắn cho vout render vài frame đầu tiên, rồi chạy
     * chu trình warm-up (bật rồi tắt ngay) trước khi đánh dấu READY.
     */
    private void markUsbCaptureStreamReady(int generation) {
        if (usbCaptureStreamReady
                || usbCaptureWarmingUp
                || generation != usbCaptureGeneration) {
            return;
        }
        Thread warmUp = new Thread(() -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
                return;
            }
            Platform.runLater(() -> {
                if (usbCaptureStreamReady
                        || usbCaptureWarmingUp
                        || !usbCaptureConnected
                        || generation != usbCaptureGeneration) {
                    return;
                }
                runUsbCaptureWarmUpCycle(generation);
            });
        }, "usb-capture-warm-up");
        warmUp.setDaemon(true);
        warmUp.start();
    }

    /**
     * !!! WORKAROUND - LƯU Ý VỀ LỖI "BẬT LẦN ĐẦU KHÔNG ỔN ĐỊNH" !!!
     *
     * Lỗi: sau khi kết nối USB CAPTURE thành công, lần BẬT overlay đầu
     * tiên hoạt động không ổn định (đen/giật); phải BẬT rồi TẮT một lần
     * thì từ lần BẬT thứ 2 trở đi mới ổn định. Nguyên nhân gốc nằm ở tầng
     * LibVLC/Direct3D + cửa sổ AWT (vout của overlay chỉ "vào đúng nhịp"
     * sau lần hiện đầu tiên lên PROGRAM OUTPUT); đã thử sửa bằng cách đổi
     * vị trí cửa sổ và chờ stream sẵn sàng nhưng chưa triệt để.
     *
     * Cách xử lý hiện tại (cố ý, ĐỪNG XOÁ nếu chưa có giải pháp gốc):
     * ngay khi kết nối xong và stream đã có hình, tự động BẬT overlay
     * khoảng USB_CAPTURE_WARMUP_SHOW_MS ms rồi TẮT lại - chính là thao
     * tác "bật rồi tắt" mà người dùng phải làm tay. Người dùng có thể
     * thấy overlay nháy rất nhanh trên PROGRAM OUTPUT đúng 1 lần. Trong
     * lúc warm-up trạng thái nút vẫn là STARTING..., nếu người dùng bấm
     * T thì được ghi nhận là "hiện sau khi warm-up xong".
     *
     * Chỉ thao tác trực tiếp trên cửa sổ overlay; KHÔNG đổi
     * usbCaptureOverlayActive và KHÔNG đụng onAirPlayer/outputCanvas.
     */
    private void runUsbCaptureWarmUpCycle(int generation) {
        if (usbCaptureOverlayFrame == null || nativeOutputFrame == null) {
            finishUsbCaptureWarmUp(generation);
            return;
        }

        usbCaptureWarmingUp = true;
        Rectangle overlayBounds = nativeOutputFrame.getBounds();

        // BẬT (giống showUsbCaptureOverlay nhưng không đổi trạng thái).
        SwingUtilities.invokeLater(() -> {
            if (generation != usbCaptureGeneration) {
                return;
            }
            if (!usbCaptureOverlayFrame.getBounds().equals(overlayBounds)) {
                usbCaptureOverlayFrame.setBounds(overlayBounds);
            }
            if (!usbCaptureOverlayFrame.isVisible()) {
                usbCaptureOverlayFrame.setVisible(true);
            }
            usbCaptureOverlayFrame.setAlwaysOnTop(true);
            usbCaptureOverlayFrame.toFront();
        });

        Thread hideLater = new Thread(() -> {
            try {
                Thread.sleep(USB_CAPTURE_WARMUP_SHOW_MS);
            } catch (InterruptedException ignored) {
                return;
            }

            // TẮT (giống hideUsbCaptureOverlay: hạ xuống dưới output frame).
            SwingUtilities.invokeLater(() -> {
                if (generation != usbCaptureGeneration
                        || usbCaptureOverlayActive) {
                    return;
                }
                usbCaptureOverlayFrame.setAlwaysOnTop(false);
                usbCaptureOverlayFrame.toBack();
            });

            // Cho AWT xử lý xong việc hạ cửa sổ rồi mới báo READY.
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
                return;
            }
            Platform.runLater(() -> finishUsbCaptureWarmUp(generation));
        }, "usb-capture-warm-up-cycle");
        hideLater.setDaemon(true);
        hideLater.start();
    }

    /** Kết thúc warm-up: đánh dấu READY, hoặc hiện luôn nếu người dùng đã bấm T trước đó. */
    private void finishUsbCaptureWarmUp(int generation) {
        if (generation != usbCaptureGeneration || !usbCaptureConnected) {
            return;
        }

        usbCaptureWarmingUp = false;
        usbCaptureStreamReady = true;

        if (usbCaptureShowPending && !usbCaptureOverlayActive) {
            usbCaptureShowPending = false;
            showUsbCaptureOverlay();
        } else {
            updateCaptureButtonAppearance();
            setStatus(
                    "USB CAPTURE ready — " + selectedUsbCaptureDeviceName
                            + (usbCaptureActiveModeLabel.isEmpty()
                            ? "" : " [" + usbCaptureActiveModeLabel + "]")
                            + " (press T to show on PROGRAM OUTPUT)"
            );
        }
    }

    /** Canvas native dùng chung cho video VLC và ảnh tĩnh. */
    private static final class OutputCanvas extends Canvas {
        private volatile BufferedImage stillImage;

        void showImage(BufferedImage image) {
            stillImage = image;
            setIgnoreRepaint(false);
            repaint();
        }

        void clearImageForVideo() {
            stillImage = null;
            setIgnoreRepaint(true);
        }

        void clearToBlack() {
            stillImage = null;
            setIgnoreRepaint(false);
            repaint();
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setColor(java.awt.Color.BLACK);
                g2.fillRect(0, 0, getWidth(), getHeight());
                BufferedImage image = stillImage;
                if (image == null) {
                    return;
                }
                double scale = Math.min(
                        getWidth() / (double) image.getWidth(),
                        getHeight() / (double) image.getHeight()
                );
                int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
                int x = (getWidth() - width) / 2;
                int y = (getHeight() - height) / 2;
                g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC
                );
                g2.drawImage(image, x, y, width, height, null);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public void update(Graphics graphics) {
            paint(graphics);
        }
    }

    private void showImageOnNativeOutput(File imageFile) {
        if (outputCanvas == null || imageFile == null) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                throw new IllegalArgumentException("Unsupported image format");
            }
            SwingUtilities.invokeLater(() -> outputCanvas.showImage(image));
        } catch (Exception exception) {
            setStatus("Unable to display image on PROGRAM OUTPUT");
        }
    }

    private void prepareNativeOutputForVideo() {
        if (outputCanvas != null) {
            SwingUtilities.invokeLater(outputCanvas::clearImageForVideo);
        }
    }

    private void clearNativeOutputToBlack() {
        if (outputCanvas != null) {
            SwingUtilities.invokeLater(outputCanvas::clearToBlack);
        }
    }

    private boolean isValidVlcDirectory(File directory) {
        return directory != null
                && directory.isDirectory()
                && new File(directory, "libvlc.dll").isFile()
                && new File(directory, "libvlccore.dll").isFile()
                && new File(directory, "plugins").isDirectory();
    }

    private void initializeState() {
        for (int index = 0; index < 4; index++) {
            visualSlots.add(null);
            audioSlots.add(null);
        }

        // Slot audio thứ 5 (nút G), hoạt động giống hệt các Slot A/S/D/F.
        audioSlots.add(null);

        idleOnAirImage = loadAppImage("on-air-idle.png");
        idleOnAirNativeImage = loadAppBufferedImage("on-air-idle.png");

        if (idleOnAirImage == null || idleOnAirNativeImage == null) {
            // Không tìm thấy ảnh ở cả classpath (/images/on-air-idle.png)
            // lẫn thư mục ngoài (<thư mục chạy app>/images/on-air-idle.png).
            // Xem console log để biết chính xác 2 đường dẫn đã thử.
            setStatus(
                    "Khong tim thay anh on-air-idle.png (xem console log de biet duong dan)"
            );
        }

        showIdleOnAirImage();
        resetVideoProgress();
        resetAudioProgress();
    }

    /**
     * Lắp watchdog theo dõi outputScaleX/outputScaleY của primaryStage.
     * Hai giá trị này phản ánh đúng tỉ lệ scale (DPI) của màn hình mà cửa
     * sổ đang thực sự đứng trên, và tự động cập nhật ngay khi cửa sổ được
     * kéo sang màn hình khác - kể cả khi người dùng không thả chuột hẳn,
     * miễn phần lớn cửa sổ đã nằm trên màn hình mới.
     *
     * Lý do cần watchdog này: JavaFX (Glass) trên Windows có lỗi khá phổ
     * biến là không tự vẽ lại đúng theo scale mới ngay khi Stage đổi màn
     * hình - gây ra đúng 2 hiện tượng đã gặp:
     * - Chữ/nút bị thu nhỏ khi kéo cửa sổ từ màn hình scale thấp về màn
     *   hình scale cao (vd FullHD 100% -> 2.8K 200%).
     * - 2 khung Preview/ON AIR (ImageView) giữ nguyên texture bitmap cũ,
     *   khiến ảnh hiển thị sai kích thước (mất một nửa diện tích) khi đổi
     *   giữa 2 màn hình có scale khác nhau (vd 2.8K 200% -> FullHD 150%).
     */
    private void installDpiChangeWatchdog() {
        if (primaryStage == null) {
            return;
        }

        primaryStage.outputScaleXProperty().addListener(
                (observable, oldScale, newScale) ->
                        Platform.runLater(this::refreshLayoutForDpiChange)
        );

        primaryStage.outputScaleYProperty().addListener(
                (observable, oldScale, newScale) ->
                        Platform.runLater(this::refreshLayoutForDpiChange)
        );
    }

    // Chặn việc gọi refreshLayoutForDpiChange() chồng chéo trong lúc đang
    // tự xử lý một lần đổi scale (vd 2 sự kiện outputScaleX/Y đến gần
    // như đồng thời khi Stage băng qua biên 2 màn hình).
    private boolean dpiRefreshInProgress;

    /**
     * Ép toàn bộ giao diện vẽ lại đúng theo scale hiện tại của màn hình
     * mà Stage đang đứng trên. Được gọi mỗi khi outputScaleX/Y đổi (xem
     * {@link #installDpiChangeWatchdog()}).
     *
     * Cách làm giống các app Windows DPI-aware "chuẩn" (vd IntelliJ IDEA
     * chạy trên JetBrains Runtime với Per-Monitor V2): khi phát hiện scale
     * đổi, KHÔNG đụng vào geometry (width/height/maximized) của cửa sổ
     * thật - người dùng sẽ thấy cửa sổ giật/nhảy kích thước. Thay vào đó,
     * ta chỉ tái tạo bề mặt render (ở đây là Scene của Stage) ở scale mới
     * rồi vẽ lại toàn bộ cây UI một lần, trong khi vị trí/kích thước cửa
     * sổ giữ nguyên tuyệt đối - kể cả khi đang maximized.
     *
     * OpenJFX (không có JBR) không expose API "rebuild render surface"
     * trực tiếp, nên gỡ Scene ra khỏi Stage rồi gắn lại là cách tương
     * đương gần nhất: nó buộc Glass tạo lại render target đúng theo
     * outputScaleX/Y hiện tại mà không làm thay đổi width/height/
     * maximized của Stage (khác hẳn cách cũ là resize ±1px hoặc tắt/bật
     * maximized).
     */
    private void refreshLayoutForDpiChange() {
        if (primaryStage == null || closing || dpiRefreshInProgress) {
            return;
        }

        Scene scene = primaryStage.getScene();

        if (scene == null) {
            return;
        }

        dpiRefreshInProgress = true;

        // 2 khung Preview/ON AIR (ImageView) cần được ép vẽ lại texture
        // theo scale mới, nếu không sẽ giữ nguyên bitmap cũ (hiển thị sai
        // kích thước) cho tới khi có ảnh mới được gán vào.
        refreshImageViewForDpiChange(previewView);
        refreshImageViewForDpiChange(onAirView);

        primaryStage.setScene(null);

        Platform.runLater(() -> {
            try {
                if (primaryStage == null || closing) {
                    return;
                }

                primaryStage.setScene(scene);

                scene.getRoot().applyCss();
                scene.getRoot().requestLayout();
            } finally {
                dpiRefreshInProgress = false;
            }
        });
    }

    private void refreshImageViewForDpiChange(ImageView imageView) {
        if (imageView == null) {
            return;
        }

        Image currentImage = imageView.getImage();
        if (currentImage == null) {
            return;
        }

        imageView.setImage(null);
        imageView.setImage(currentImage);
    }

    private void configureVlcEvents() {
        onAirPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {

            @Override
            public void lengthChanged(MediaPlayer mediaPlayer, long length) {
                Platform.runLater(() -> {
                    if (onAirIsImage) {
                        return;
                    }

                    videoProgressSlider.setMin(0);
                    videoProgressSlider.setMax(Math.max(1, length));
                    videoProgressSlider.setDisable(false);
                });
            }

            @Override
            public void timeChanged(MediaPlayer mediaPlayer, long time) {
                Platform.runLater(() -> {
                    if (!onAirIsImage && !videoProgressSlider.isValueChanging()) {
                        videoProgressSlider.setValue(time);
                    }
                });
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    if (closing || onAirIsImage) {
                        return;
                    }

                    /*
                     * Video kết thúc trước thì dừng audio ngoài.
                     */
                    stopAndReleaseExternalAudio();

                    setOnAirRunning(false);

                    resetVideoProgress();
                    setLiveState(false);

                    /*
                     * Nếu AUTO PLAY đang bật và Preview có nội dung,
                     * chuyển Preview lên ON AIR.
                     */
                    if (autoPlayButton.isSelected()
                            && selectedVisualFile != null
                            && selectedVisualFile.isFile()) {

                        setStatus(
                                "ON AIR has ended, playing the next Preview"
                        );

                        playWaitingPreviewAfterOnAirFinished();

                    } else {
                        showIdleOnAirImage();

                        setStatus(
                                "ON AIR video has ended"
                        );
                    }
                });
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    if (closing) {
                        return;
                    }

                    stopAndReleaseExternalAudio();

                    setOnAirRunning(false);

                    resetVideoProgress();
                    showIdleOnAirImage();
                    setLiveState(false);

                    /*
                     * Tắt AUTO PLAY nếu nội dung hiện tại bị lỗi.
                     */
                    if (autoPlayButton.isSelected()) {
                        autoPlayButton.setSelected(false);
                    }

                    setStatus(
                            "Error playing ON AIR video"
                    );
                });
            }

            /*
             * Nhận ảnh snapshot định kỳ (xem startOnAirThumbnailRefresh)
             * để cập nhật khung "PROGRAM OUTPUT" nhỏ trong Studio Center.
             * Đây là cách duy nhất còn lại để hiển thị nội dung ON AIR
             * trong app mà không phải chạy 2 pipeline giải mã CPU nặng.
             */

        });
    }

    /**
     * Theo dõi Screen.getScreens() (JavaFX tự cập nhật danh sách này khi hệ
     * điều hành báo thay đổi cấu hình màn hình) để phát hiện khi màn hình
     * vật lý đang được dùng làm PROGRAM OUTPUT (selectedOutputScreen) bị
     * RÚT/mất tín hiệu đột ngột (tháo cáp HDMI, tắt màn hình rời...). Theo
     * yêu cầu, phản ứng bằng cách tự khởi động lại toàn bộ app ngay lập
     * tức, thay vì cố xử lý/khôi phục trạng thái bên trong app đang chạy.
     */
    private void configureScreenConnectionMonitoring() {
        Screen.getScreens().addListener(
                (javafx.collections.ListChangeListener<Screen>) change -> {
                    if (!outputScreenOpen
                            || selectedOutputScreen == null) {
                        return;
                    }

                    if (!Screen.getScreens().contains(selectedOutputScreen)) {
                        restartApplication(
                                "PROGRAM OUTPUT screen signal was lost unexpectedly"
                        );
                    }
                }
        );
    }

    /**
     * Khởi động watchdog định kỳ dò lại Device Manager (mục "Cameras") để
     * bắt trường hợp usbCapturePlayer KHÔNG bắn error()/stopped()/finished()
     * khi thiết bị bị rút/disable (xem giải thích ở khai báo
     * usbCaptureDeviceWatchdogTimeline). Chạy suốt vòng đời app; tự bỏ qua
     * khi chưa có USB CAPTURE nào đang kết nối.
     */
    private void configureUsbCaptureDeviceWatchdog() {
        if (usbCaptureDeviceWatchdogTimeline != null) {
            return;
        }

        usbCaptureDeviceWatchdogTimeline = new Timeline(
                new KeyFrame(
                        Duration.millis(USB_CAPTURE_DEVICE_WATCHDOG_INTERVAL_MS),
                        event -> checkUsbCaptureDevicePresence()
                )
        );
        usbCaptureDeviceWatchdogTimeline.setCycleCount(Timeline.INDEFINITE);
        usbCaptureDeviceWatchdogTimeline.play();
    }

    /**
     * Một nhịp watchdog: nếu đang có USB CAPTURE kết nối, dò lại danh sách
     * thiết bị (PowerShell Get-PnpDevice, giống hệt listVideoCaptureDevices())
     * ở thread nền, rồi so sánh với tên thiết bị đang dùng. Không tìm thấy
     * nữa (đã rút cáp hoặc đã bị disable trong Device Manager) trong khi
     * app vẫn nghĩ là đang kết nối -> restart ngay, đúng yêu cầu.
     */
    private void checkUsbCaptureDevicePresence() {
        if (isRestarting
                || !usbCaptureConnected
                || selectedUsbCaptureDeviceName == null
                || usbCaptureDeviceCheckInFlight) {
            return;
        }

        usbCaptureDeviceCheckInFlight = true;
        final String deviceNameAtCheckTime = selectedUsbCaptureDeviceName;

        Thread checkThread = new Thread(() -> {
            List<String> devices;
            try {
                devices = listVideoCaptureDevices();
            } finally {
                usbCaptureDeviceCheckInFlight = false;
            }

            // Danh sách rỗng cũng có thể do PowerShell tạm thời lỗi/chậm
            // (không có nghĩa là mọi camera đều đã rút) - chỉ coi là MẤT
            // THIẾT BỊ khi dò được danh sách và thiết bị không còn trong đó.
            if (devices.isEmpty()) {
                return;
            }

            if (!devices.contains(deviceNameAtCheckTime)) {
                Platform.runLater(() -> {
                    // Kiểm tra lại lần nữa trên FX thread: người dùng có
                    // thể đã tự đổi/ngắt thiết bị trong lúc PowerShell chạy.
                    if (usbCaptureConnected
                            && deviceNameAtCheckTime.equals(selectedUsbCaptureDeviceName)) {
                        restartApplication(
                                "USB CAPTURE device \"" + deviceNameAtCheckTime
                                        + "\" is no longer present in Device Manager"
                        );
                    }
                });
            }
        }, "usb-capture-device-watchdog");
        checkThread.setDaemon(true);
        checkThread.start();
    }

    /**
     * Tự khởi động lại toàn bộ ứng dụng: chạy lại đúng lệnh (java + toàn bộ
     * tham số) đã dùng để khởi chạy tiến trình hiện tại - cách này hoạt
     * động bất kể app được chạy bằng "java -jar ...", classpath thủ công,
     * hay file .exe native (jpackage) - rồi thoát tiến trình hiện tại.
     * Chỉ chạy MỘT lần (isRestarting) để tránh trường hợp cả USB CAPTURE
     * và PROGRAM OUTPUT cùng báo mất tín hiệu một lúc kích hoạt restart 2 lần.
     */
    private void restartApplication(String reason) {
        if (isRestarting) {
            return;
        }
        isRestarting = true;

        setStatus(reason + " - restarting the app...");

        try {
            ProcessHandle.Info currentInfo = ProcessHandle.current().info();
            List<String> command = new ArrayList<>();
            currentInfo.command().ifPresent(command::add);
            currentInfo.arguments().ifPresent(
                    args -> command.addAll(java.util.Arrays.asList(args))
            );

            if (!command.isEmpty()) {
                new ProcessBuilder(command)
                        .inheritIO()
                        .start();
            }
        } catch (Exception exception) {
            // Không còn cách nào khác để tự phục hồi từ đây - vẫn tiếp tục
            // thoát ở dưới; người dùng sẽ cần tự mở lại app trong tình
            // huống hiếm gặp này (ví dụ không lấy được command line gốc).
        }

        Platform.exit();
        System.exit(0);
    }

    /**
     * Bắt đầu làm mới khung "PROGRAM OUTPUT" trong Studio Center bằng
     * snapshot mỗi giây thay vì nhận live video frame liên tục - tránh
     * việc phải chạy lại pipeline CPU nặng chỉ để hiển thị 1 khung nhỏ.
     */
    private void startOnAirThumbnailRefresh() {
        if (onAirThumbnailTimeline != null) {
            return;
        }
        onAirThumbnailTimeline = new Timeline(
                new KeyFrame(
                        Duration.seconds(1),
                        event -> requestOnAirThumbnailSnapshot()
                )
        );
        onAirThumbnailTimeline.setCycleCount(Timeline.INDEFINITE);
        onAirThumbnailTimeline.play();
        requestOnAirThumbnailSnapshot();
    }

    private void stopOnAirThumbnailRefresh() {
        if (onAirThumbnailTimeline != null) {
            onAirThumbnailTimeline.stop();
            onAirThumbnailTimeline = null;
        }
    }

    private void requestOnAirThumbnailSnapshot() {
        if (outputCanvas == null || !outputScreenOpen
                || onAirIsImage || !onAirRunning
                || !outputCanvas.isShowing()) {
            return;
        }

        /*
         * Không dùng API snapshot của VLC vì API đó tạo ô nhỏ trên Output.
         * Chỉ chụp pixel của Canvas đã hiển thị để cập nhật khung viền đỏ.
         */
        SwingUtilities.invokeLater(() -> {
            try {
                if (!outputCanvas.isShowing()) {
                    return;
                }
                Point location = outputCanvas.getLocationOnScreen();
                Dimension size = outputCanvas.getSize();
                if (size.width <= 0 || size.height <= 0) {
                    return;
                }
                Rectangle captureArea = new Rectangle(
                        location.x, location.y, size.width, size.height
                );
                BufferedImage frame = new Robot().createScreenCapture(captureArea);

                /*
                 * Chuyển BufferedImage sang JavaFX Image chỉ bằng thư viện
                 * chuẩn, không cần module JavaFX Swing.
                 */
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ImageIO.write(frame, "png", outputStream);
                byte[] imageBytes = outputStream.toByteArray();

                Platform.runLater(() -> {
                    if (onAirRunning && !onAirIsImage) {
                        try (ByteArrayInputStream inputStream =
                                     new ByteArrayInputStream(imageBytes)) {
                            Image thumbnail = new Image(inputStream);
                            if (!thumbnail.isError()) {
                                onAirView.setImage(thumbnail);
                            }
                        } catch (Exception ignored) {
                            // Chu kỳ tiếp theo sẽ tự cập nhật lại.
                        }
                    }
                });
            } catch (Exception ignored) {
                // Chu kỳ kế tiếp sẽ tự thử lại.
            }
        });
    }

    private void configureProgressSliders() {
        videoProgressSlider.setOnMouseReleased(event -> {
            if (onAirPlayer != null && !onAirIsImage) {
                onAirPlayer.controls().setTime((long) videoProgressSlider.getValue());
            }
        });

        audioProgressSlider.setOnMouseReleased(event -> {
            if (externalAudioPlayer != null) {
                externalAudioPlayer.controls().setTime((long) audioProgressSlider.getValue());
            }
        });
    }

    private HBox createTopBar() {
        Label title = new Label("DisplayFX");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label(
                "LIVE MEDIA WORKSPACE — shortcuts require a US/English keyboard layout"
        );

        subtitle.getStyleClass().add(
                "app-subtitle"
        );

        VBox titleBox = new VBox(
                1,
                title,
                subtitle
        );

        Region spacer = new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );

        liveIndicator.getStyleClass().add(
                "offline-indicator"
        );

        HBox topBar = new HBox(
                10,
                titleBox,
                spacer,
                liveIndicator
        );

        topBar.setAlignment(
                Pos.CENTER_LEFT
        );

        topBar.setPadding(
                new Insets(8, 14, 8, 14)
        );

        topBar.getStyleClass().add(
                "top-bar"
        );

        return topBar;
    }

    private SplitPane createMainContent() {
        VBox workspace = createWorkspace();
        VBox folderPanel = createFolderPanel();

        workspace.setMinWidth(0);

        folderPanel.setMinWidth(200);
        folderPanel.setPrefWidth(280);
        folderPanel.setMaxWidth(360);

        SplitPane splitPane = new SplitPane(
                workspace,
                folderPanel
        );

        splitPane.setOrientation(
                Orientation.HORIZONTAL
        );

        splitPane.setDividerPositions(0.80);

        Platform.runLater(() ->
                splitPane.setDividerPositions(0.80)
        );

        return splitPane;
    }

    private VBox createWorkspace() {
        VBox onAirPanel = createOnAirPanel();
        VBox previewPanel = createPreviewPanel();

        /*
         * Khung ON AIR (viền đỏ) và khung PREVIEW (viền xanh lá) nằm trong
         * hai VBox riêng biệt, mỗi VBox có số lượng và chiều cao control
         * bên dưới khác nhau (Preview có thêm 2 media-row), nên nếu để
         * tự do "grow" thì hai khung sẽ không cao bằng nhau dù panel cha
         * cao bằng nhau. Ràng buộc chiều cao ON AIR theo đúng chiều cao
         * thực tế của Preview để hai khung luôn khớp nhau.
         */
        onAirDisplayPane.maxHeightProperty().bind(
                previewDisplayPane.heightProperty()
        );

        onAirPanel.setMinSize(0, 0);
        onAirPanel.setPrefWidth(0);

        previewPanel.setMinSize(0, 0);
        previewPanel.setPrefWidth(0);

        SplitPane monitorArea = new SplitPane();

        monitorArea.setOrientation(
                Orientation.HORIZONTAL
        );

        monitorArea.getItems().addAll(
                onAirPanel,
                previewPanel
        );

        monitorArea.setMinSize(0, 0);
        monitorArea.setDividerPositions(0.5);

        /*
         * Chỉ khu vực monitor nhận phần chiều cao còn lại.
         */
        VBox.setVgrow(
                monitorArea,
                Priority.ALWAYS
        );

        VBox quickControlPanel =
                createQuickControlPanel();

        /*
         * QUICK ACCESS chỉ sử dụng chiều cao vừa đủ
         * cho nội dung, không nhận thêm khoảng trống.
         */
        quickControlPanel.setMinHeight(
                Region.USE_PREF_SIZE
        );

        quickControlPanel.setPrefHeight(
                Region.USE_COMPUTED_SIZE
        );

        quickControlPanel.setMaxHeight(
                Region.USE_PREF_SIZE
        );

        VBox.setVgrow(
                quickControlPanel,
                Priority.NEVER
        );

        VBox workspace = new VBox(
                10,
                monitorArea,
                quickControlPanel
        );

        workspace.setMinSize(0, 0);
        workspace.setPadding(new Insets(10));
        workspace.getStyleClass().add("workspace");

        Platform.runLater(() ->
                monitorArea.setDividerPositions(0.5)
        );

        return workspace;
    }


    private VBox createOnAirPanel() {
        StackPane display = createDisplayPane(
                onAirView,
                null,
                "on-air-display"
        );

        onAirDisplayPane = display;

        VBox.setVgrow(
                display,
                Priority.ALWAYS
        );

        resetVideoProgress();
        resetAudioProgress();
        configureAutoPlayButton();

        selectScreenButton =
                createSecondaryButton(
                        "SELECT SCREEN"
                );

        selectScreenButton.setOnAction(event ->
                showScreenSelectionDialog()
        );

        stopButton =
                createSecondaryButton("STOP");

        stopButton.setOnAction(event ->
                stopOnAir()
        );

        // Trạng thái ban đầu: chưa chọn màn hình -> đỏ; ON AIR chưa chạy -> nút STOP bình thường.
        updateSelectScreenButtonAppearance();
        updateStopButtonAppearance();
        updateTakeButtonAvailability();

        autoPlayButton.setMinWidth(0);
        selectScreenButton.setMinWidth(0);
        stopButton.setMinWidth(0);

        HBox actionBox = new HBox(
                8,
                autoPlayButton,
                selectScreenButton,
                stopButton
        );

        actionBox.setMinHeight(
                Region.USE_PREF_SIZE
        );

        HBox.setHgrow(
                autoPlayButton,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                selectScreenButton,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                stopButton,
                Priority.ALWAYS
        );

        VBox panel = new VBox(
                8,
                createSectionTitle("PROGRAM OUTPUT (1-second sampling period)"),
                display,
                createFieldLabel("VIDEO POSITION"),
                videoProgressSlider,
                createFieldLabel("AUDIO POSITION"),
                audioProgressSlider,
                actionBox
        );

        panel.setMinSize(0, 0);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("monitor-panel");

        return panel;
    }

    private void configureAutoPlayButton() {
        autoPlayButton.getStyleClass().add(
                "auto-play-button"
        );

        autoPlayButton.setMaxWidth(
                Double.MAX_VALUE
        );

        updateAutoPlayAppearance(false);

        autoPlayButton.selectedProperty().addListener(
                (observable, oldValue, enabled) -> {
                    updateAutoPlayAppearance(enabled);

                    if (enabled) {
                        if (selectedVisualFile == null) {
                            setStatus(
                                    "AUTO PLAY is enabled, no content is queued"
                            );
                        } else {
                            setStatus(
                                    "AUTO PLAY is enabled, Preview will play after ON AIR ends"
                            );
                        }
                    } else {
                        setStatus("AUTO PLAY is disabled");
                    }
                }
        );
    }

    private void updateAutoPlayAppearance(boolean enabled) {
        // on / off
        autoPlayButton.setText(enabled ? "AUTO PLAY: ON " : "AUTO PLAY: OFF");

        autoPlayButton.getStyleClass().removeAll(
                "auto-play-enabled",
                "auto-play-disabled"
        );

        autoPlayButton.getStyleClass().add(
                enabled ? "auto-play-enabled" : "auto-play-disabled"
        );
    }

    private VBox createPreviewPanel() {
        StackPane display = createDisplayPane(
                previewView,
                previewPlaceholder,
                "preview-display"
        );

        previewDisplayPane = display;

        VBox.setVgrow(
                display,
                Priority.ALWAYS
        );

        browseVisualButton =
                createCompactButton("BROWSE");

        browseVisualButton.setOnAction(event ->
                handleVisualBrowseOrDelete()
        );

        browseAudioButton =
                createCompactButton("BROWSE");

        browseAudioButton.setOnAction(event ->
                handleAudioBrowseOrDelete()
        );

        // Đặt đúng trạng thái ban đầu (BROWSE nếu Preview chưa có file).
        refreshVisualBrowseButton();
        refreshAudioBrowseButton();

        HBox visualRow = createMediaRow(
                "VIDEO / IMAGE",
                visualNameLabel,
                browseVisualButton,
                visualReadyLabel
        );

        HBox audioRow = createMediaRow(
                "AUDIO",
                audioNameLabel,
                browseAudioButton,
                audioReadyLabel
        );

        generalVolumeSlider
                .valueProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                updateGeneralVolume()
                );

        takeButton =
                new Button("TAKE  →  ON AIR");

        takeButton.getStyleClass().add(
                "take-button"
        );

        takeButton.setMaxWidth(
                Double.MAX_VALUE
        );

        // Cố định chiều cao (không dùng USE_PREF_SIZE) để nút giữ nguyên
        // kích cỡ khi đổi giữa style "take-button" và "secondary-button"
        // (enabled/disabled), tránh làm xê dịch layout xung quanh.
        takeButton.setMinHeight(48);
        takeButton.setPrefHeight(48);
        takeButton.setMaxHeight(48);

        takeButton.setOnAction(event ->
                takePreviewToOnAir()
        );

        // Chỉ cho phép TAKE -> ON AIR khi đã chọn màn hình PROGRAM OUTPUT.
        updateTakeButtonAvailability();

        VBox panel = new VBox(
                8,
                previewSectionTitle,
                display,
                visualRow,
                audioRow,
                createFieldLabel("GENERAL VOLUME"),
                generalVolumeSlider,
                takeButton
        );

        panel.setMinSize(0, 0);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("monitor-panel");

        return panel;
    }

    /**
     * Bật/tắt nút "TAKE -> ON AIR" tuỳ theo đã chọn màn hình PROGRAM OUTPUT
     * hay chưa, VÀ Preview hiện có nội dung hợp lệ để TAKE hay không. Khi bị
     * disable, đổi sang cùng kiểu (style class) với nút STOP lúc ON AIR
     * không chạy, để người dùng nhận biết rõ nút đang bị khoá. Khi hợp lệ,
     * nút sáng đỏ (style "take-button") như cũ.
     *
     * Hợp lệ khi:
     * - đã chọn màn hình PROGRAM OUTPUT, VÀ
     * - Preview có video/ảnh, HOẶC Preview chỉ có audio nhưng đang có một
     *   ảnh ON AIR sẵn (cho phép đổi audio ON AIR mà không cần chọn lại ảnh).
     */
    private void updateTakeButtonAvailability() {
        if (takeButton == null) {
            return;
        }

        boolean screenSelected = selectedOutputScreen != null;

        boolean hasVisual =
                selectedVisualFile != null
                        && selectedVisualFile.isFile();

        boolean hasAudio =
                selectedAudioFile != null
                        && selectedAudioFile.isFile()
                        && isAudioFile(selectedAudioFile);

        // Audio-only TAKE chỉ hợp lệ khi đang có sẵn một ảnh ON AIR.
        boolean audioOnlyValid = hasAudio && onAirIsImage;

        boolean contentValid = hasVisual || audioOnlyValid;

        boolean canTake = screenSelected && contentValid;

        takeButton.setDisable(!canTake);

        takeButton.getStyleClass().removeAll(
                "take-button",
                "secondary-button"
        );

        takeButton.getStyleClass().add(
                canTake ? "take-button" : "secondary-button"
        );
    }

    /**
     * Đặt trạng thái onAirRunning và luôn đồng bộ lại màu nút STOP theo đó.
     * Dùng thay cho việc gán trực tiếp "onAirRunning = ...;" ở các nơi khác.
     */
    private void setOnAirRunning(boolean running) {
        onAirRunning = running;
        updateStopButtonAppearance();
    }

    /**
     * Đổi màu nút STOP: đỏ khi ON AIR đang chạy, bình thường khi không chạy.
     */
    private void updateStopButtonAppearance() {
        if (stopButton == null) {
            return;
        }

        stopButton.getStyleClass().remove("stop-button-active");

        if (onAirRunning) {
            stopButton.getStyleClass().add("stop-button-active");
        }
    }

    /**
     * Đổi màu nút SELECT SCREEN: đỏ khi chưa chọn màn hình, xanh khi đã chọn.
     */
    private void updateSelectScreenButtonAppearance() {
        if (selectScreenButton == null) {
            return;
        }

        boolean screenSelected = selectedOutputScreen != null;

        selectScreenButton.getStyleClass().removeAll(
                "select-screen-selected",
                "select-screen-unselected"
        );

        selectScreenButton.getStyleClass().add(
                screenSelected ? "select-screen-selected" : "select-screen-unselected"
        );
    }


    private StackPane createDisplayPane(
            ImageView imageView,
            Label placeholder,
            String styleClass
    ) {
        StackPane displayPane =
                new StackPane();

        /*
         * Quan trọng: cho phép vùng video co xuống.
         */
        displayPane.setMinWidth(0);
        displayPane.setMinHeight(100);

        displayPane.setPrefWidth(0);
        displayPane.setPrefHeight(270);

        displayPane.setMaxWidth(
                Double.MAX_VALUE
        );

        displayPane.setMaxHeight(
                Double.MAX_VALUE
        );

        displayPane.getStyleClass().addAll(
                "media-display",
                styleClass
        );

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setMouseTransparent(true);

        imageView.fitWidthProperty().bind(
                displayPane
                        .widthProperty()
                        .subtract(16)
        );

        imageView.fitHeightProperty().bind(
                displayPane
                        .heightProperty()
                        .subtract(16)
        );

        displayPane.getChildren().add(
                imageView
        );

        if (placeholder != null) {
            placeholder.getStyleClass().add(
                    "display-placeholder"
            );

            displayPane.getChildren().add(
                    placeholder
            );
        }

        return displayPane;
    }

    private HBox createMediaRow(
            String title,
            Label fileNameLabel,
            Button browseButton,
            Label readyLabel
    ) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("media-type-label");
        titleLabel.setMinWidth(105);
        titleLabel.setPrefWidth(105);

        fileNameLabel.getStyleClass().add("file-name-label");
        fileNameLabel.setMinWidth(0);
        fileNameLabel.setMaxWidth(Double.MAX_VALUE);
        fileNameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(fileNameLabel.textProperty());
        fileNameLabel.setTooltip(tooltip);

        browseButton.setMinWidth(Region.USE_PREF_SIZE);
        readyLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(fileNameLabel, Priority.ALWAYS);

        HBox row = new HBox(
                8,
                titleLabel,
                fileNameLabel,
                browseButton,
                readyLabel
        );

        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("media-row");
        return row;
    }

    private VBox createQuickControlPanel() {
        GridPane visualGrid = new GridPane();
        GridPane audioGrid = new GridPane();

        visualGrid.setHgap(10);
        audioGrid.setHgap(10);

        /*
         * Giữ chiều cao hai hàng nhỏ gọn.
         */
        visualGrid.setMinHeight(58);
        visualGrid.setPrefHeight(58);
        visualGrid.setMaxHeight(58);

        audioGrid.setMinHeight(42);
        audioGrid.setPrefHeight(42);
        audioGrid.setMaxHeight(42);

        String[] visualKeys = {
                "Q", "W", "E", "R"
        };

        String[] audioKeys = {
                "A", "S", "D", "F"
        };

        for (int index = 0; index < 4; index++) {
            final int slotIndex = index;

            Button visualButton = new Button(
                    visualKeys[index]
                            + "\nVIDEO / IMAGE "
                            + (index + 1)
            );

            visualButton.getStyleClass().add(
                    "video-slot"
            );

            visualButton.setMinHeight(58);
            visualButton.setPrefHeight(58);
            visualButton.setMaxHeight(58);
            visualButton.setMaxWidth(Double.MAX_VALUE);

            visualButton.setOnAction(event ->
                    handleVisualSlot(
                            slotIndex,
                            visualButton
                    )
            );

            configureVisualSlotDrop(
                    visualButton,
                    slotIndex,
                    visualKeys[index]
            );

            visualGrid.add(
                    visualButton,
                    index,
                    0
            );

            visualSlotButtons[slotIndex] = visualButton;

            Button audioButton = new Button(
                    audioKeys[index]
                            + "  AUDIO "
                            + (index + 1)
            );

            audioButton.getStyleClass().add(
                    "audio-slot"
            );

            audioButton.setMinHeight(42);
            audioButton.setPrefHeight(42);
            audioButton.setMaxHeight(42);
            audioButton.setMaxWidth(Double.MAX_VALUE);

            audioButton.setOnAction(event ->
                    handleAudioSlot(
                            slotIndex,
                            audioButton
                    )
            );

            configureAudioSlotDrop(
                    audioButton,
                    slotIndex,
                    audioKeys[index]
            );

            audioGrid.add(
                    audioButton,
                    index,
                    0
            );

            audioSlotButtons[slotIndex] = audioButton;

            addTwentyPercentColumn(visualGrid);
            addTwentyPercentColumn(audioGrid);
        }

        captureButton =
                new Button("T\nUSB CAPTURE");

        captureButton.getStyleClass().add(
                "capture-button"
        );

        captureButton.setMinHeight(58);
        captureButton.setPrefHeight(58);
        captureButton.setMaxHeight(58);
        captureButton.setMaxWidth(Double.MAX_VALUE);

        captureButton.setOnAction(event ->
                toggleUsbCaptureOverlay()
        );

        visualGrid.add(
                captureButton,
                4,
                0
        );

        addTwentyPercentColumn(visualGrid);

        Button captureAudioButton =
                new Button("G  AUDIO 5");

        captureAudioButton.getStyleClass().add(
                "audio-slot"
        );

        captureAudioButton.setMinHeight(42);
        captureAudioButton.setPrefHeight(42);
        captureAudioButton.setMaxHeight(42);
        captureAudioButton.setMaxWidth(Double.MAX_VALUE);

        captureAudioButton.setOnAction(event ->
                handleAudioSlot(
                        4,
                        captureAudioButton
                )
        );

        configureAudioSlotDrop(
                captureAudioButton,
                4,
                "G"
        );

        audioGrid.add(
                captureAudioButton,
                4,
                0
        );

        audioSlotButtons[4] = captureAudioButton;

        addTwentyPercentColumn(audioGrid);

        VBox panel = new VBox(
                8,
                createSectionTitle("QUICK ACCESS"),
                visualGrid,
                audioGrid
        );

        /*
         * Panel chỉ cao vừa đủ nội dung.
         */
        panel.setMinHeight(
                Region.USE_PREF_SIZE
        );

        panel.setPrefHeight(
                Region.USE_COMPUTED_SIZE
        );

        panel.setMaxHeight(
                Region.USE_PREF_SIZE
        );

        panel.setPadding(
                new Insets(10)
        );

        panel.getStyleClass().add(
                "quick-panel"
        );

        return panel;
    }

    private void addTwentyPercentColumn(GridPane gridPane) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(20);
        column.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().add(column);
    }

    private VBox createFolderPanel() {
        folderListView.setPlaceholder(new Label("The folder contains no media"));
        folderListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                setText(empty || file == null ? null : file.getName());
            }
        });

        folderListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                File file = folderListView.getSelectionModel().getSelectedItem();
                if (file != null) {
                    loadFileFromFolder(file);
                }
            }
        });

        folderListView.setOnDragDetected(event -> {
            File file = folderListView.getSelectionModel().getSelectedItem();
            if (file == null) {
                return;
            }

            Dragboard dragboard = folderListView.startDragAndDrop(
                    TransferMode.COPY
            );
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(file));
            dragboard.setContent(content);
            event.consume();
        });

        VBox.setVgrow(folderListView, Priority.ALWAYS);

        Button openButton = createPrimaryButton("OPEN FOLDER");
        openButton.setMaxWidth(Double.MAX_VALUE);
        openButton.setOnAction(event -> openMediaFolder());

        Button clearButton = createSecondaryButton("CLEAR LIST");
        clearButton.setOnAction(event -> folderListView.getItems().clear());

        Label descriptionLabel = new Label("Drag files into the Slots");
        descriptionLabel.getStyleClass().add("panel-description");

        VBox panel = new VBox(
                10,
                createSectionTitle("MEDIA FOLDER"),
                descriptionLabel,
                folderListView,
                openButton,
                clearButton
        );

        panel.setPrefWidth(300);
        panel.setMinWidth(230);
        panel.setPadding(new Insets(14));
        panel.getStyleClass().add("folder-panel");
        return panel;
    }

    private HBox createStatusBar() {
        Label shortcutLabel = new Label(
                "Q/W/E/R: Select Visual | A/S/D/F/G: Select Audio | ENTER: TAKE ON AIR"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statusBar = new HBox(
                10,
                shortcutLabel,
                spacer,
                statusLabel
        );
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(7, 15, 7, 15));
        statusBar.getStyleClass().add("status-bar");
        return statusBar;
    }

    private void selectVisual() {
        FileChooser chooser = createVisualChooser(
                "Select a video or image"
        );

        File file = null;

        try {
            file = chooser.showOpenDialog(
                    primaryStage
            );
        } finally {
            /*
             * Native FileChooser có thể làm Output mất
             * thứ tự Always On Top trên Windows.
             */
            Platform.runLater(
                    this::restoreOutputScreenWindow
            );
        }

        if (file != null) {
            loadMediaToPreview(file);
        }
    }

    private void selectAudio() {
        File file = createAudioChooser(
                "Select audio"
        ).showOpenDialog(primaryStage);

        if (file != null) {
            prepareAudio(file);
        }
    }

    /**
     * Nút BROWSE của hàng VIDEO/IMAGE: nếu Preview đang trống thì mở hộp
     * thoại chọn file như cũ; nếu Preview đã có file thì nút này biến
     * thành DELETE để gỡ file đó ra khỏi Preview.
     */
    private void handleVisualBrowseOrDelete() {
        if (selectedVisualFile != null) {
            deleteVisualFromPreview();
        } else {
            selectVisual();
        }
    }

    /**
     * Tương tự handleVisualBrowseOrDelete(), áp dụng cho hàng AUDIO.
     */
    private void handleAudioBrowseOrDelete() {
        if (selectedAudioFile != null) {
            deleteAudioFromPreview();
        } else {
            selectAudio();
        }
    }

    /**
     * Gỡ video/ảnh đang chờ ra khỏi Preview (người dùng đổi ý sau khi đã chọn).
     * Nếu file này đến từ một Slot,
     * Slot đó cũng được trả về trạng thái trống.(bỏ)
     */
    private void deleteVisualFromPreview() {
        stopPreviewResolutionCheck();
        previewPlayer.controls().stop();
        previewView.setImage(null);
        previewView.setVisible(true);
        previewSectionTitle.setText("PREVIEW");
        previewPlaceholder.setText("PREVIEW");
        previewPlaceholder.setVisible(true);

        selectedVisualFile = null;
        previewIsImage = false;
        visualNameLabel.setText("No video / image selected");
        setStateLabel(visualReadyLabel, "NO FILE", false);

//        if (activeVisualSlotIndex != null) {
//            resetVisualSlot(activeVisualSlotIndex);
//            activeVisualSlotIndex = null;
//        }

        refreshVisualBrowseButton();
        setStatus("Visual removed from Preview");
    }

    /**
     * Gỡ audio đang chờ ra khỏi Preview (người dùng đổi ý sau khi đã chọn).
     * Nếu file này đến từ một Slot,
     * Slot đó cũng được trả về trạng thái trống.(bỏ)
     */
    private void deleteAudioFromPreview() {
        selectedAudioFile = null;
        audioNameLabel.setText("No audio selected");
        setStateLabel(audioReadyLabel, "NO FILE", false);

//        if (activeAudioSlotIndex != null) {
//            resetAudioSlot(activeAudioSlotIndex);
//            activeAudioSlotIndex = null;
//        }

        refreshAudioBrowseButton();
        setStatus("Audio removed from Preview");
    }

    /**
     * Đổi nhãn/kiểu của nút hàng VIDEO/IMAGE: "BROWSE" khi Preview trống,
     * "DELETE" (kèm màu cảnh báo) khi Preview đã có file.
     */
    private void refreshVisualBrowseButton() {
        boolean hasFile = selectedVisualFile != null;
        browseVisualButton.setText(hasFile ? "DELETE" : "BROWSE");
        browseVisualButton.getStyleClass().remove("compact-button-delete");

        if (hasFile) {
            browseVisualButton.getStyleClass().add("compact-button-delete");
            browseVisualButton.setStyle("-fx-background-color: #b23b3b; -fx-text-fill: white;");
        } else {
            browseVisualButton.setStyle("");
        }

        updateTakeButtonAvailability();
    }

    /**
     * Tương tự refreshVisualBrowseButton(), áp dụng cho nút hàng AUDIO.
     */
    private void refreshAudioBrowseButton() {
        boolean hasFile = selectedAudioFile != null;
        browseAudioButton.setText(hasFile ? "DELETE" : "BROWSE");
        browseAudioButton.getStyleClass().remove("compact-button-delete");

        if (hasFile) {
            browseAudioButton.getStyleClass().add("compact-button-delete");
            browseAudioButton.setStyle("-fx-background-color: #b23b3b; -fx-text-fill: white;");
        } else {
            browseAudioButton.setStyle("");
        }

        updateTakeButtonAvailability();
    }

    private void loadMediaToPreview(File file) {
        loadMediaToPreview(file, null);
    }

    private void loadMediaToPreview(File file, Integer slotIndex) {
        if (file == null || !file.isFile()) {
            setStatus("File does not exist");
            return;
        }

        // Preview không bao giờ phát âm thanh.
        stopPreviewResolutionCheck();
        previewPlayer.controls().stop();
        previewView.setImage(null);
        previewView.setVisible(true);
        previewPlaceholder.setText("PREVIEW");

        selectedVisualFile = file;
        activeVisualSlotIndex = slotIndex;
        visualNameLabel.setText(file.getName());
        setStateLabel(visualReadyLabel, " READY ", true);

        if (isImageFile(file)) {
            previewIsImage = true;
            previewSectionTitle.setText("PREVIEW");
            previewView.setVisible(true);
            Image image = new Image(file.toURI().toString(), false);

            if (image.isError()) {
                clearVisualPreview();
                setStatus("Unable to open image");
                return;
            }

            previewView.setImage(image);
            previewPlaceholder.setVisible(false);
            setStatus("Image is waiting for TAKE → ON AIR");
        } else if (isVideoFile(file)) {
            previewIsImage = false;

            /*
             * Cho phép VLC hiển thị frame đầu để xác nhận video đã được chọn.
             * Sau khi phát hiện video 4K+, player giữ trạng thái pause tại
             * frame đầu và không tiếp tục phát Preview.
             */
            previewView.setVisible(true);
            previewSectionTitle.setText("PREVIEW");
            previewPlaceholder.setText("CHECKING RESOLUTION...");
            previewPlaceholder.setVisible(true);

            boolean loaded = previewPlayer.media().startPaused(
                    file.getAbsolutePath()
            );

            // Bảo đảm Preview luôn im lặng kể cả khi thư viện đổi trạng thái.
            previewPlayer.controls().pause();

            if (!loaded) {
                clearVisualPreview();
                setStatus("Unable to load video into Preview");
                return;
            }

            checkVideoResolutionBeforeShowingPreview(file);
        } else {
            clearVisualPreview();
            setStatus("Visual format is not supported");
            return;
        }

        refreshVisualBrowseButton();
    }

    /**
     * Video 4K trở lên vẫn được xếp hàng để TAKE ON AIR, nhưng không hiển thị
     * trong Preview nhằm tránh duy trì việc giải mã video độ phân giải cao.
     */
    private void checkVideoResolutionBeforeShowingPreview(File expectedFile) {
        stopPreviewResolutionCheck();
        previewResolutionCheckAttempts = 0;
        previewView.setVisible(false);
        previewPlaceholder.setText("CHECKING RESOLUTION...");
        previewPlaceholder.setVisible(true);

        previewResolutionCheckTimeline = new Timeline(
                new KeyFrame(Duration.millis(100), event -> {
                    if (selectedVisualFile == null
                            || !selectedVisualFile.equals(expectedFile)
                            || previewIsImage) {
                        stopPreviewResolutionCheck();
                        return;
                    }
                    previewResolutionCheckAttempts++;
                    Dimension dimension = previewPlayer.video().videoDimension();
                    if (dimension == null || dimension.width <= 0 || dimension.height <= 0) {
                        if (previewResolutionCheckAttempts >= PREVIEW_RESOLUTION_CHECK_MAX_ATTEMPTS) {
                            previewPlayer.controls().stop();
                            previewView.setImage(null);
                            previewView.setVisible(false);
                            previewSectionTitle.setText("PREVIEW UNAVAILABLE");
                            previewPlaceholder.setText("PREVIEW UNAVAILABLE");
                            previewPlaceholder.setVisible(true);
                            setStatus("Unable to determine resolution; Preview disabled");
                            stopPreviewResolutionCheck();
                        }
                        return;
                    }

                    boolean isFourKOrHigher = dimension.width >= FOUR_K_MIN_WIDTH
                            || dimension.height >= FOUR_K_MIN_HEIGHT;
                    if (isFourKOrHigher) {
                        /*
                         * Giữ nguyên frame đầu trên ImageView để người dùng
                         * biết video đã được chọn thành công. Chỉ pause, không
                         * stop và không xóa ảnh, nên Preview không tiếp tục chạy.
                         */
                        previewPlayer.controls().pause();
                        previewView.setVisible(true);
                        previewSectionTitle.setText(
                                "4K+ PREVIEW DISABLED FOR REDUCE GPU"
                        );
                        previewPlaceholder.setText("PREVIEW");
                        previewPlaceholder.setVisible(false);
                        setStatus("4K+ video queued; first frame is shown: "
                                + expectedFile.getName());
                    } else {
                        previewSectionTitle.setText("PREVIEW");
                        previewView.setVisible(true);
                        previewPlaceholder.setText("PREVIEW");
                        previewPlaceholder.setVisible(false);
                        setStatus("Video is waiting for TAKE → ON AIR");
                    }
                    stopPreviewResolutionCheck();
                })
        );
        previewResolutionCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        previewResolutionCheckTimeline.play();
    }

    private void stopPreviewResolutionCheck() {
        if (previewResolutionCheckTimeline != null) {
            previewResolutionCheckTimeline.stop();
            previewResolutionCheckTimeline = null;
        }
    }

    private void prepareAudio(File file) {
        prepareAudio(file, null);
    }

    private void prepareAudio(File file, Integer slotIndex) {
        if (file == null || !file.isFile() || !isAudioFile(file)) {
            setStatus("Invalid audio file");
            return;
        }

        // Chọn audio chỉ đưa vào trạng thái chờ, hoàn toàn không phát.
        selectedAudioFile = file;
        activeAudioSlotIndex = slotIndex;
        audioNameLabel.setText(file.getName());
        setStateLabel(audioReadyLabel, " READY ", true);
        setStatus("Video is waiting for TAKE → ON AIR");

        refreshAudioBrowseButton();
    }

    private void playWaitingPreviewAfterOnAirFinished() {
        /*
         * Chỉ chạy khi AUTO PLAY đang bật.
         */
        if (!autoPlayButton.isSelected()) {
            return;
        }

        boolean hasVisualWaiting =
                selectedVisualFile != null
                        && selectedVisualFile.isFile();

        boolean hasAudioWaiting =
                selectedAudioFile != null
                        && selectedAudioFile.isFile()
                        && isAudioFile(selectedAudioFile);

        /*
         * Preview không có video/ảnh, nhưng nếu ON AIR hiện đang là một ảnh
         * và Preview có sẵn audio đang chờ (trường hợp audio kèm ảnh), vẫn
         * cho AUTO PLAY tự TAKE audio đó lên ON AIR, giữ nguyên ảnh.
         */
        if (!hasVisualWaiting) {
            if (hasAudioWaiting && onAirIsImage) {
                if (takingToOnAir) {
                    return;
                }

                Platform.runLater(() -> {
                    if (!autoPlayButton.isSelected()) {
                        return;
                    }

                    if (selectedAudioFile == null
                            || !selectedAudioFile.isFile()) {
                        autoPlayButton.setSelected(false);
                        return;
                    }

                    takeAudioOnlyToOnAir(selectedAudioFile);
                });

                return;
            }

            autoPlayButton.setSelected(false);

            setStatus(
                    "ON AIR has ended, but Preview has no content"
            );

            return;
        }

        /*
         * Tránh gọi TAKE nhiều lần.
         */
        if (takingToOnAir) {
            return;
        }

        /*
         * Chờ JavaFX hoàn tất callback của nội dung cũ,
         * sau đó mới chuyển Preview lên ON AIR.
         */
        Platform.runLater(() -> {
            if (!autoPlayButton.isSelected()) {
                return;
            }

            if (selectedVisualFile == null
                    || !selectedVisualFile.isFile()) {
                autoPlayButton.setSelected(false);
                return;
            }

            takePreviewToOnAir();
        });
    }

    private void takePreviewToOnAir() {
        if (takingToOnAir) {
            return;
        }

        // Không cho TAKE -> ON AIR khi chưa chọn màn hình PROGRAM OUTPUT
        // (phòng trường hợp gọi qua phím tắt ENTER thay vì bấm nút).
        if (selectedOutputScreen == null) {
            setStatus(
                    "Please select PROGRAM OUTPUT screen before TAKE -> ON AIR"
            );

            return;
        }

        boolean hasVisual =
                selectedVisualFile != null
                        && selectedVisualFile.isFile();

        if (!hasVisual) {
            /*
             * Không có video/ảnh trong Preview: nếu đang có sẵn một ảnh
             * ON AIR và Preview có audio hợp lệ, cho phép TAKE audio-only,
             * giữ nguyên ảnh đang ON AIR hiện tại thay vì bắt buộc phải
             * chọn lại ảnh.
             */
            boolean hasAudio =
                    selectedAudioFile != null
                            && selectedAudioFile.isFile()
                            && isAudioFile(selectedAudioFile);

            if (hasAudio && onAirIsImage) {
                takeAudioOnlyToOnAir(selectedAudioFile);
            } else {
                setStatus(
                        "No video or image is queued"
                );
            }

            return;
        }

        takingToOnAir = true;

        /*
         * Lưu nội dung trước khi làm trống Preview.
         */
        File visualFile = selectedVisualFile;
        File audioFile = selectedAudioFile;

        boolean disableAutoPlayAfterTake =
                autoPlayButton.isSelected();

        /*
         * Làm trống Preview ngay khi TAKE.
         */
        clearPreviewCompletely();

        stopOnAirMediaOnly();
        stopAndReleaseExternalAudio();

        boolean hasExternalAudio =
                audioFile != null
                        && audioFile.isFile()
                        && isAudioFile(audioFile);

        if (isImageFile(visualFile)) {
            takeImageToOnAir(
                    visualFile,
                    audioFile,
                    hasExternalAudio
            );
        } else {
            takeVideoToOnAir(
                    visualFile,
                    audioFile,
                    hasExternalAudio
            );
        }

        takingToOnAir = false;

        /*
         * AUTO PLAY là chế độ chỉ chạy một lần.
         * Tắt sau khi nội dung mới đã được đưa lên ON AIR.
         */
        if (disableAutoPlayAfterTake) {
            autoPlayButton.setSelected(false);
        }
    }

    /**
     * TAKE -> ON AIR chỉ dành cho audio: dùng khi Preview không có video/ảnh
     * nhưng đang có sẵn một ảnh ON AIR. Chỉ thay audio đang phát trên ON AIR,
     * giữ nguyên ảnh ON AIR hiện tại (không đụng tới onAirPlayer/onAirView).
     */
    private void takeAudioOnlyToOnAir(File audioFile) {
        takingToOnAir = true;

        boolean disableAutoPlayAfterTake =
                autoPlayButton.isSelected();

        /*
         * Chỉ làm trống phần audio của Preview, KHÔNG đụng tới ảnh đang
         * ON AIR (onAirIsImage, onAirView, native output vẫn giữ nguyên).
         */
        clearAudioFromPreview();

        stopAndReleaseExternalAudio();
        playExternalAudio(audioFile, false);

        setStatus(
                "ON AIR audio: "
                        + audioFile.getName()
                        + " (image on air unchanged)"
        );

        takingToOnAir = false;

        if (disableAutoPlayAfterTake) {
            autoPlayButton.setSelected(false);
        }
    }

    /**
     * Làm trống chỉ phần audio của Preview (dùng bởi takeAudioOnlyToOnAir),
     * tương tự phần audio trong clearPreviewCompletely() nhưng không đụng
     * tới Preview video/ảnh.
     */
    private void clearAudioFromPreview() {
        selectedAudioFile = null;
        audioNameLabel.setText("No audio selected");
        setStateLabel(audioReadyLabel, "NO FILE", false);

        if (activeAudioSlotIndex != null) {
            resetAudioSlot(activeAudioSlotIndex);
            activeAudioSlotIndex = null;
        }

        refreshAudioBrowseButton();
    }

    private void takeImageToOnAir(
            File imageFile,
            File audioFile,
            boolean hasExternalAudio
    ) {
        stopOnAirThumbnailRefresh();

        onAirIsImage = true;
        updateTakeButtonAvailability();
        setOnAirRunning(true);
        onAirPlayer.controls().stop();

        Image image = new Image(imageFile.toURI().toString(), false);
        if (image.isError()) {
            showIdleOnAirImage();
            setLiveState(false);
            setStatus("Unable to put image ON AIR");
            return;
        }

        onAirView.setImage(image);
        showImageOnNativeOutput(imageFile);
        resetVideoProgress();
        setLiveState(true);

        if (hasExternalAudio) {
            playExternalAudio(audioFile, false);
            setStatus(
                    "ON AIR image: "
                            + imageFile.getName()
                            + " + "
                            + audioFile.getName()
            );
        } else {
            resetAudioProgress();
            setStatus("ON AIR image: " + imageFile.getName());
        }

    }

    private void takeVideoToOnAir(
            File videoFile,
            File audioFile,
            boolean hasExternalAudio
    ) {
        onAirIsImage = false;
        updateTakeButtonAvailability();

        onAirView.setImage(null);
        onAirPlayer.controls().stop();
        prepareNativeOutputForVideo();

        boolean videoStarted;

        if (hasExternalAudio) {
            /*
             * Không khởi tạo luồng âm thanh gốc của video.
             *
             * Không chỉ dùng setMute(true), vì VLC có thể đặt lại
             * trạng thái mute khi media mới bắt đầu phát.
             */
            videoStarted = onAirPlayer
                    .media()
                    .play(
                            videoFile.getAbsolutePath(),
                            ":no-audio"
                    );
        } else {
            /*
             * Không có audio ngoài:
             * phát video với âm thanh gốc.
             */
            onAirPlayer.audio().setMute(false);
            onAirPlayer.audio().setVolume(
                    getGeneralVolume()
            );

            videoStarted = onAirPlayer
                    .media()
                    .play(
                            videoFile.getAbsolutePath()
                    );
        }

        if (!videoStarted) {
            setOnAirRunning(false);
            showIdleOnAirImage();
            setLiveState(false);

            setStatus(
                    "Unable to play ON AIR video."
            );


            return;
        }

        setOnAirRunning(true);
        startOnAirThumbnailRefresh();

        if (hasExternalAudio) {
            /*
             * Video đang chạy hoàn toàn không có audio output.
             * Phát audio ngoài bằng player riêng.
             */
            playExternalAudio(
                    audioFile,
                    true
            );

            setStatus(
                    "ON AIR: "
                            + videoFile.getName()
                            + " + "
                            + audioFile.getName()
            );
        } else {
            resetAudioProgress();

            setStatus(
                    "ON AIR: "
                            + videoFile.getName()
                            + " | Audio gốc"
            );
        }

        setLiveState(true);
    }

    private void playExternalAudio(
            File audioFile,
            boolean attachedToVideo
    ) {
        stopAndReleaseExternalAudio();

        externalAudioPlayer = outputVlcFactory
                .mediaPlayers()
                .newMediaPlayer();
        MediaPlayer currentPlayer = externalAudioPlayer;
        currentPlayer.audio().setVolume(getGeneralVolume());

        currentPlayer.events().addMediaPlayerEventListener(
                new MediaPlayerEventAdapter() {

                    @Override
                    public void lengthChanged(MediaPlayer mediaPlayer, long length) {
                        Platform.runLater(() -> {
                            if (externalAudioPlayer != currentPlayer) {
                                return;
                            }

                            audioProgressSlider.setMin(0);
                            audioProgressSlider.setMax(Math.max(1, length));
                            audioProgressSlider.setDisable(false);
                        });
                    }

                    @Override
                    public void timeChanged(MediaPlayer mediaPlayer, long time) {
                        Platform.runLater(() -> {
                            if (externalAudioPlayer == currentPlayer
                                    && !audioProgressSlider.isValueChanging()) {
                                audioProgressSlider.setValue(time);
                            }
                        });
                    }

                    @Override
                    public void finished(MediaPlayer mediaPlayer) {
                        Platform.runLater(() -> {
                            if (externalAudioPlayer != currentPlayer) {
                                return;
                            }

                            externalAudioPlayer = null;
                            resetAudioProgress();
                            currentPlayer.release();

                            // Audio hết không được dừng video hoặc xóa ảnh.
                            if (onAirIsImage && !attachedToVideo) {
                                /*
                                 * Trường hợp ảnh + audio (audio CHÍNH LÀ nội
                                 * dung ON AIR, không phải audio đi kèm nền
                                 * video): audio kết thúc nghĩa là nội dung
                                 * ON AIR đã kết thúc, giống hệt lúc video kết
                                 * thúc. Nếu AUTO PLAY đang bật và Preview có
                                 * nội dung mới (video/ảnh hoặc chỉ audio),
                                 * tự động TAKE nội dung đó lên ON AIR.
                                 */
                                if (autoPlayButton.isSelected()) {
                                    setStatus(
                                            "ON AIR audio has ended, playing the next Preview"
                                    );

                                    playWaitingPreviewAfterOnAirFinished();
                                } else {
                                    setStatus("Audio has ended, image remains ON AIR");
                                }
                            } else if (attachedToVideo) {
                                setStatus("Audio has ended, video continues playing until the end.");
                            }
                        });
                    }

                    @Override
                    public void error(MediaPlayer mediaPlayer) {
                        Platform.runLater(() -> {
                            if (externalAudioPlayer != currentPlayer) {
                                return;
                            }

                            externalAudioPlayer = null;
                            resetAudioProgress();
                            currentPlayer.release();

                            if (!onAirIsImage && attachedToVideo) {
                                onAirPlayer.audio().setMute(false);
                                onAirPlayer.audio().setVolume(getGeneralVolume());
                                setStatus("External audio error, restored the video's original audio");
                            } else {
                                setStatus("Unable to play external audio, image remains ON AIR");
                            }
                        });
                    }
                });

        boolean started = currentPlayer.media().play(
                audioFile.getAbsolutePath()
        );

        if (!started) {
            stopAndReleaseExternalAudio();

            if (!onAirIsImage && attachedToVideo) {
                onAirPlayer.audio().setMute(false);
                onAirPlayer.audio().setVolume(getGeneralVolume());
            }
        }
    }

    private void clearPreviewCompletely() {
        stopPreviewResolutionCheck();
        previewPlayer.controls().stop();
        previewView.setImage(null);
        previewView.setVisible(true);
        previewSectionTitle.setText("PREVIEW");
        previewPlaceholder.setText("PREVIEW");
        previewPlaceholder.setVisible(true);

        selectedVisualFile = null;
        selectedAudioFile = null;
        previewIsImage = false;

        visualNameLabel.setText("No video / image selected");
        audioNameLabel.setText("No audio selected");
        setStateLabel(visualReadyLabel, "NO FILE", false);
        setStateLabel(audioReadyLabel, "NO FILE", false);

        /*
         * Nội dung vừa được TAKE lên ON AIR không còn ở trạng thái "chờ"
         * nữa, nên Slot đã cung cấp nội dung đó (nếu có) cũng phải quay
         * về trạng thái trống, giống hệt Preview.
         */
        if (activeVisualSlotIndex != null) {
            resetVisualSlot(activeVisualSlotIndex);
            activeVisualSlotIndex = null;
        }

        if (activeAudioSlotIndex != null) {
            resetAudioSlot(activeAudioSlotIndex);
            activeAudioSlotIndex = null;
        }

        refreshVisualBrowseButton();
        refreshAudioBrowseButton();
    }

    private void clearVisualPreview() {
        stopPreviewResolutionCheck();
        previewPlayer.controls().stop();
        previewView.setImage(null);
        previewView.setVisible(true);
        previewSectionTitle.setText("PREVIEW");
        previewPlaceholder.setText("PREVIEW");
        previewPlaceholder.setVisible(true);

        selectedVisualFile = null;
        activeVisualSlotIndex = null;
        previewIsImage = false;
        visualNameLabel.setText("No video / image selected");
        setStateLabel(visualReadyLabel, "NO FILE", false);

        refreshVisualBrowseButton();
    }

    private void stopOnAir() {
        stopOnAirMediaOnly();
        stopAndReleaseExternalAudio();

        setOnAirRunning(false);

        showIdleOnAirImage();
        setLiveState(false);

        /*
         * STOP thủ công không được kích hoạt AUTO PLAY.
         */
        if (autoPlayButton.isSelected()) {
            autoPlayButton.setSelected(false);
        }

        setStatus(
                "all ON AIR content has been stopped."
        );
    }

    private void stopOnAirMediaOnly() {
        if (onAirPlayer != null) {
            onAirPlayer.controls().stop();
        }

        onAirIsImage = false;
        updateTakeButtonAvailability();
        resetVideoProgress();
    }

    private void stopAndReleaseExternalAudio() {
        MediaPlayer oldPlayer = externalAudioPlayer;
        externalAudioPlayer = null;

        if (oldPlayer != null) {
            oldPlayer.controls().stop();
            oldPlayer.release();
        }

        resetAudioProgress();
    }

    private void showIdleOnAirImage() {
        stopOnAirThumbnailRefresh();
        onAirIsImage = false;
        updateTakeButtonAvailability();
        onAirView.setImage(idleOnAirImage);
        showIdleImageOnNativeOutput();
    }

    /**
     * Hiển thị on-air-idle.png lên MÀN HÌNH ĐANG TRÌNH CHIẾU (PROGRAM OUTPUT
     * - cửa sổ native gắn trên outputCanvas), không chỉ khung ON AIR nhỏ
     * trong Studio Center. Nếu không có ảnh idle (chưa tìm thấy file) thì
     * mới về lại nền đen như cũ.
     */
    private void showIdleImageOnNativeOutput() {
        if (outputCanvas == null) {
            return;
        }

        if (idleOnAirNativeImage != null) {
            SwingUtilities.invokeLater(
                    () -> outputCanvas.showImage(idleOnAirNativeImage)
            );
        } else {
            SwingUtilities.invokeLater(outputCanvas::clearToBlack);
        }
    }

    /**
     * Bấm USB CAPTURE (nút hoặc phím tắt "T"):
     * - CHƯA kết nối: mở dialog chọn thiết bị video capture (giống cách
     *   SELECT SCREEN cho PROGRAM OUTPUT). Sau khi chọn xong, thiết bị
     *   được KẾT NỐI ngay (usbCapturePlayer bắt đầu phát) nhưng CHƯA hiện
     *   lên PROGRAM OUTPUT - nút chuyển sang trạng thái "READY" (viền
     *   vàng), chờ bấm lần nữa mới hiện.
     * - Đã kết nối nhưng đang TẮT (READY): hiện ngay lớp đè hình lên
     *   PROGRAM OUTPUT (không phải kết nối lại thiết bị, nên gần như tức
     *   thời) - nút chuyển "ACTIVE" (viền xanh lá).
     * - Đã kết nối và đang BẬT (ACTIVE): chỉ ẨN lớp đè hình, KHÔNG ngắt
     *   kết nối thiết bị USB - usbCapturePlayer vẫn tiếp tục phát ở chế
     *   độ nền, sẵn sàng hiện lại ngay lập tức ở lần bấm kế tiếp. Video/
     *   ảnh đang ON AIR phía dưới không hề bị đụng tới trong mọi trường
     *   hợp trên.
     */
    private void toggleUsbCaptureOverlay() {
        if (usbCaptureOverlayActive) {
            hideUsbCaptureOverlay("USB CAPTURE turned off (still connected, ready to show)");
            return;
        }

        if (usbCaptureConnected) {
            if (!usbCaptureStreamReady) {
                // Stream chưa lên hình: đợi rồi tự hiện (bấm lần nữa = huỷ).
                usbCaptureShowPending = !usbCaptureShowPending;
                updateCaptureButtonAppearance();
                setStatus(
                        usbCaptureShowPending
                                ? "USB CAPTURE is starting... will show automatically when video is ready"
                                : "USB CAPTURE auto-show cancelled"
                );
                return;
            }
            showUsbCaptureOverlay();
            return;
        }

        if (!outputScreenOpen
                || selectedOutputScreen == null
                || nativeOutputFrame == null) {
            setStatus(
                    "Please SELECT SCREEN for PROGRAM OUTPUT before turning on USB CAPTURE"
            );
            return;
        }

        if (usbCapturePlayer == null || usbCaptureOverlayFrame == null) {
            setStatus("USB CAPTURE is not ready yet (VLC not initialized).");
            return;
        }

        showUsbCaptureDeviceDialog();
    }

    /**
     * Dò danh sách thiết bị video capture đang cắm vào máy (mục "Cameras"
     * trong Device Manager - giống ảnh chụp người dùng gửi) bằng lệnh
     * PowerShell "Get-PnpDevice", rồi hiển thị dialog cho người dùng chọn
     * đúng thiết bị muốn dùng làm nguồn USB CAPTURE - tương tự dialog
     * "Select Output Screen". Việc dò thiết bị chạy ở thread riêng để
     * không đứng hình Studio Center trong lúc chờ PowerShell khởi động.
     */
    private void showUsbCaptureDeviceDialog() {
        setStatus("Looking for USB video capture devices...");

        Thread deviceLookupThread = new Thread(() -> {
            List<String> devices = listVideoCaptureDevices();
            Platform.runLater(() -> presentUsbCaptureDeviceDialog(devices));
        }, "usb-capture-device-lookup");
        deviceLookupThread.setDaemon(true);
        deviceLookupThread.start();
    }

    /**
     * Liệt kê tên các thiết bị trong nhóm "Cameras" của Windows Device
     * Manager (đúng những gì hiển thị cho người dùng, ví dụ "USB Video",
     * "Integrated Camera"...). Dùng PowerShell có sẵn trên Windows thay
     * vì tự enumerate DirectShow bằng COM/JNA - vừa đơn giản vừa cho ra
     * đúng tên friendly-name mà VLC dùng cho ":dshow-vdev=".
     */
    private List<String> listVideoCaptureDevices() {
        List<String> devices = new ArrayList<>();

        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command",
                    "Get-PnpDevice -Class Camera -PresentOnly | "
                            + "Select-Object -ExpandProperty FriendlyName"
            );
            builder.redirectErrorStream(true);

            Process process = builder.start();

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                            process.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8
                    )
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String deviceName = line.trim();
                    if (!deviceName.isEmpty()) {
                        devices.add(deviceName);
                    }
                }
            }

            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception exception) {
            // Không dò được bằng PowerShell -> trả về danh sách rỗng,
            // dialog sẽ tự báo cho người dùng biết.
        }

        return devices;
    }

    /**
     * Hiển thị dialog (dạng ComboBox, cùng phong cách với dialog SELECT
     * SCREEN) để người dùng chọn đúng thiết bị video capture muốn dùng.
     * Sau khi OK, thiết bị được chọn sẽ được dùng làm nguồn cho lớp đè
     * hình USB CAPTURE.
     */
    private void presentUsbCaptureDeviceDialog(List<String> devices) {
        if (devices.isEmpty()) {
            setStatus(
                    "No video capture device found (check Windows Device Manager > Cameras)."
            );
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Select USB Capture Device");
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(false);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("screen-dialog");
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> deviceComboBox =
                new ComboBox<>(FXCollections.observableArrayList(devices));
        deviceComboBox.setMaxWidth(Double.MAX_VALUE);

        String initialDevice;
        if (selectedUsbCaptureDeviceName != null
                && devices.contains(selectedUsbCaptureDeviceName)) {
            initialDevice = selectedUsbCaptureDeviceName;
        } else if (devices.contains(CAPTURE_VIDEO_DEVICE)) {
            initialDevice = CAPTURE_VIDEO_DEVICE;
        } else {
            initialDevice = devices.get(0);
        }
        deviceComboBox.setValue(initialDevice);

        Label hintLabel = new Label(
                "Select the input device that will be shown as an overlay "
                        + "on top of the current PROGRAM OUTPUT."
        );
        hintLabel.setWrapText(true);

        VBox content = new VBox(12, hintLabel, deviceComboBox);
        content.setPadding(new Insets(16));
        dialogPane.setContent(content);

        dialogPane.setMinWidth(420);
        dialogPane.setPrefWidth(460);

        Node okButton = dialogPane.lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(
                deviceComboBox.valueProperty().isNull()
        );

        dialog.setResultConverter(buttonType ->
                buttonType == ButtonType.OK ? deviceComboBox.getValue() : null
        );

        dialog.showAndWait().ifPresent(this::connectUsbCapture);
    }

    /**
     * Rút gọn tên thiết bị để hiển thị vừa trên nút (tránh tràn chữ với
     * những tên thiết bị dài), tên đầy đủ vẫn được dùng để mở device và
     * hiển thị trong status bar / tooltip.
     */
    private String truncateForButtonLabel(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    /**
     * KẾT NỐI (mở) thiết bị USB CAPTURE người dùng vừa chọn ở dialog.
     *
     * Khác với trước đây: kết nối xong KHÔNG hiện lớp đè hình lên PROGRAM
     * OUTPUT ngay - cửa sổ overlay được đặt ra NGOÀI vùng hiển thị (xem
     * USB_CAPTURE_OFFSCREEN_X/Y) để usbCapturePlayer vẫn phát bình
     * thường (giữ kết nối "nóng" với thiết bị), nhưng người dùng không
     * thấy gì cho tới khi chủ động bấm BẬT (xem showUsbCaptureOverlay).
     *
     * KHÔNG stop/pause/đổi bất cứ gì trên onAirPlayer: video/ảnh đang
     * ON AIR, audio của nó, và AUTO PLAY vẫn tiếp tục chạy bình thường.
     *
     * ":dshow-adev=none" đảm bảo không lấy audio từ thiết bị video này.
     */
    private void connectUsbCapture(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return;
        }

        if (usbCapturePlayer == null || usbCaptureOverlayFrame == null) {
            setStatus("USB CAPTURE is not ready yet (VLC not initialized).");
            return;
        }

        if (!outputScreenOpen
                || selectedOutputScreen == null
                || nativeOutputFrame == null) {
            setStatus(
                    "Please SELECT SCREEN for PROGRAM OUTPUT before turning on USB CAPTURE"
            );
            return;
        }

        /*
         * QUAN TRỌNG: vlcj yêu cầu Canvas của video surface phải
         * "displayable" (đã có native peer) NGAY TẠI THỜI ĐIỂM gọi
         * media().play() - nếu không sẽ ném
         * "IllegalStateException: The video surface component must be
         * displayable". Vì vậy phải setVisible(true) cho
         * usbCaptureOverlayFrame và CHỜ (invokeAndWait) cho AWT dựng
         * xong native peer trước, rồi mới được gọi play() ngay sau đó.
         * Dùng invokeLater ở đây (chạy bất đồng bộ) là nguyên nhân gây
         * lỗi vì play() chạy trước khi cửa sổ kịp hiển thị.
         *
         * FIX lỗi "phải bật từ lần 2 mới ổn định": trước đây cửa sổ được
         * đặt NGOÀI màn hình (-32000,-32000) khi kết nối. LibVLC (Direct3D)
         * khởi tạo vout/swapchain theo màn hình mà cửa sổ đang nằm, nên khi
         * lần BẬT đầu tiên kéo cửa sổ từ toạ độ ảo vào màn hình thật, vout
         * bị sai adapter/kích thước -> hình đen/giật, tới lần BẬT thứ 2 mới ổn.
         *
         * Giờ cửa sổ được đặt NGAY từ đầu ĐÚNG vị trí + kích thước của
         * PROGRAM OUTPUT nhưng KHÔNG always-on-top, nên luôn nằm DƯỚI
         * nativeOutputFrame (đang always-on-top) => "kết nối" mà không
         * "hiện", và vout đã ở đúng màn hình/kích thước sẵn. Lần BẬT chỉ cần
         * đưa lên trên cùng, không phải di chuyển/resize nữa.
         */
        try {
            SwingUtilities.invokeAndWait(() -> {
                usbCaptureOverlayFrame.setBounds(nativeOutputFrame.getBounds());
                usbCaptureOverlayFrame.setAlwaysOnTop(false);
                usbCaptureOverlayFrame.setVisible(true);
                usbCaptureOverlayFrame.toBack();
            });
        } catch (Exception exception) {
            setStatus("Unable to open USB Capture overlay window.");
            return;
        }

        usbCaptureGeneration++;
        final int generation = usbCaptureGeneration;
        usbCaptureStreamReady = false;
        usbCaptureShowPending = false;
        usbCaptureWarmingUp = false;
        // Đánh dấu "đang kết nối" TRƯỚC play() để handler sự kiện (chạy
        // trên thread khác) không bị loại nhầm vì usbCaptureConnected chưa set.
        usbCaptureConnected = true;
        usbCaptureActiveModeLabel = "";

        selectedUsbCaptureDeviceName = deviceName;
        usbCaptureOverlayActive = false;
        updateCaptureButtonAppearance();
        setStatus(
                "USB CAPTURE connecting — " + deviceName
                        + " (detecting supported resolution...)"
        );

        /*
         * Việc thử từng mode phải chạy NGOÀI FX thread vì mỗi lần thử có thể
         * phải chờ vài giây để biết thiết bị có lên hình hay không.
         */
        Thread connectThread = new Thread(
                () -> openUsbCaptureWithBestMode(deviceName, generation),
                "usb-capture-connect"
        );
        connectThread.setDaemon(true);
        connectThread.start();
    }

    /**
     * Thử lần lượt các mode trong {@link #USB_CAPTURE_MODES} cho tới khi
     * thiết bị THỰC SỰ có hình. Khác cách cũ (dựa vào giá trị trả về của
     * media().play() - luôn true vì VLC mở thiết bị bất đồng bộ nên mode
     * đầu tiên luôn "thành công" dù lỗi), ở đây một mode chỉ được chấp nhận
     * khi:
     *  - nhận được sự kiện videoOutput, hoặc
     *  - hết timeout mà player vẫn đang PLAYING và không báo lỗi (driver
     *    không phát videoOutput).
     * Gặp error/stopped/finished thì bỏ mode đó ngay và thử mode kế tiếp.
     */
    private void openUsbCaptureWithBestMode(String deviceName, int generation) {
        boolean opened = false;

        for (String[] mode : USB_CAPTURE_MODES) {
            if (generation != usbCaptureGeneration || !usbCaptureConnected) {
                return; // người dùng đã ngắt kết nối/đổi thiết bị
            }

            // Dừng lần thử trước và chờ các sự kiện stopped "cũ" trôi hết
            // rồi mới reset cờ, tránh coi nhầm là lần thử mới bị lỗi.
            usbCapturePlayer.controls().stop();
            try {
                Thread.sleep(300);
            } catch (InterruptedException interrupted) {
                return;
            }
            usbCaptureAttemptVideoSeen = false;
            usbCaptureAttemptFailed = false;

            List<String> options = new ArrayList<>();
            options.add(":dshow-vdev=" + deviceName);
            options.add(":dshow-adev=none");
            String label;
            if (mode[0] != null) {
                options.add(":dshow-size=" + mode[0]);
                options.add(":dshow-aspect-ratio=" + aspectRatioOf(mode[0]));
                label = mode[0];
            } else {
                label = "device default";
            }
            if (mode[1] != null) {
                options.add(":dshow-fps=" + mode[1]);
                label += " " + mode[1] + "fps";
            }
            if (mode[2] != null) {
                options.add(":dshow-chroma=" + mode[2]);
                label += " " + mode[2];
            }
            options.add(":live-caching=100");

            final String tryingLabel = label;
            Platform.runLater(() -> {
                if (generation == usbCaptureGeneration && usbCaptureConnected
                        && !usbCaptureStreamReady) {
                    setStatus("USB CAPTURE connecting — " + deviceName
                            + " (trying " + tryingLabel + ")");
                }
            });

            boolean started = usbCapturePlayer.media().play(
                    "dshow://",
                    options.toArray(new String[0])
            );
            if (!started) {
                continue;
            }

            long deadline = System.currentTimeMillis() + USB_CAPTURE_MODE_TIMEOUT_MS;
            boolean videoSeen = false;
            while (System.currentTimeMillis() < deadline) {
                if (generation != usbCaptureGeneration || !usbCaptureConnected) {
                    return;
                }
                if (usbCaptureAttemptVideoSeen) {
                    videoSeen = true;
                    break;
                }
                if (usbCaptureAttemptFailed) {
                    break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }

            boolean success = videoSeen;
            if (!success && !usbCaptureAttemptFailed
                    && generation == usbCaptureGeneration
                    && usbCapturePlayer.status().isPlaying()) {
                // Driver không phát videoOutput nhưng vẫn đang chạy, không lỗi.
                success = true;
                markUsbCaptureStreamReady(generation);
            }

            if (success) {
                usbCaptureActiveModeLabel = label;
                opened = true;
                final String okLabel = label;
                Platform.runLater(() -> {
                    if (generation == usbCaptureGeneration && usbCaptureConnected
                            && !usbCaptureStreamReady) {
                        setStatus("USB CAPTURE connecting — " + deviceName
                                + " [" + okLabel + "]"
                                + " (press T to show; it appears as soon as video is ready)");
                    }
                });
                break;
            }
        }

        if (!opened && generation == usbCaptureGeneration) {
            Platform.runLater(() -> {
                if (generation == usbCaptureGeneration && usbCaptureConnected) {
                    disconnectUsbCapture(
                            "Unable to open USB Capture device: " + deviceName
                                    + " (no supported video mode found; it may be in use by"
                                    + " another app or blocked by Windows camera privacy)"
                    );
                }
            });
        }
    }

    /** "1920x1080" -> "16:9", "640x480" -> "4:3" (dùng cho :dshow-aspect-ratio). */
    private static String aspectRatioOf(String size) {
        try {
            String[] parts = size.split("x");
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            int a = width;
            int b = height;
            while (b != 0) {
                int t = a % b;
                a = b;
                b = t;
            }
            return (width / a) + ":" + (height / a);
        } catch (Exception exception) {
            return "16:9";
        }
    }

    /**
     * BẬT: hiện lớp đè hình USB CAPTURE (đã kết nối sẵn) lên đúng vị trí
     * PROGRAM OUTPUT. Không kết nối lại thiết bị nên gần như tức thời.
     */
    private void showUsbCaptureOverlay() {
        if (!usbCaptureConnected
                || usbCapturePlayer == null
                || usbCaptureOverlayFrame == null) {
            return;
        }

        if (!outputScreenOpen
                || selectedOutputScreen == null
                || nativeOutputFrame == null) {
            setStatus(
                    "Please SELECT SCREEN for PROGRAM OUTPUT before turning on USB CAPTURE"
            );
            return;
        }

        Rectangle overlayBounds = nativeOutputFrame.getBounds();

        SwingUtilities.invokeLater(() -> {
            // Chỉ set lại bounds khi thật sự lệch (tránh resize vout không cần thiết).
            if (!usbCaptureOverlayFrame.getBounds().equals(overlayBounds)) {
                usbCaptureOverlayFrame.setBounds(overlayBounds);
            }
            if (!usbCaptureOverlayFrame.isVisible()) {
                usbCaptureOverlayFrame.setVisible(true);
            }
            usbCaptureOverlayFrame.setAlwaysOnTop(true);
            usbCaptureOverlayFrame.toFront();
        });

        usbCaptureOverlayActive = true;
        updateCaptureButtonAppearance();
        setStatus("USB CAPTURE : ACTIVE — " + selectedUsbCaptureDeviceName);
    }

    /**
     * TẮT: chỉ ẨN lớp đè hình USB CAPTURE (đưa cửa sổ ra ngoài màn hình
     * + bỏ always-on-top) - KHÔNG dừng usbCapturePlayer, KHÔNG giải
     * phóng thiết bị USB. Kết nối vẫn còn nguyên ("giữ ở đó để chờ dùng
     * tiếp"), nên lần BẬT kế tiếp chỉ cần showUsbCaptureOverlay(), không
     * cần mở lại dialog chọn thiết bị.
     */
    private void hideUsbCaptureOverlay(String statusMessage) {
        if (!usbCaptureOverlayActive) {
            return;
        }

        usbCaptureOverlayActive = false;
        updateCaptureButtonAppearance();

        if (usbCaptureOverlayFrame != null) {
            SwingUtilities.invokeLater(() -> {
                // Giữ nguyên vị trí/kích thước, chỉ hạ xuống DƯỚI nativeOutputFrame.
                usbCaptureOverlayFrame.setAlwaysOnTop(false);
                usbCaptureOverlayFrame.toBack();
            });
        }

        if (statusMessage != null) {
            setStatus(statusMessage);
        }
    }

    /**
     * NGẮT KẾT NỐI hẳn USB CAPTURE: dừng usbCapturePlayer (giải phóng
     * thiết bị USB thật sự) và ẩn hẳn cửa sổ overlay. Chỉ gọi khi không
     * còn PROGRAM OUTPUT hợp lệ để hiện overlay lên (đổi/đóng SELECT
     * SCREEN) hoặc khi đóng app - KHÔNG gọi mỗi khi người dùng bấm TẮT
     * thông thường (xem hideUsbCaptureOverlay). onAirPlayer/outputCanvas
     * không hề bị đụng tới, nên PROGRAM OUTPUT hiện lại đúng nội dung
     * ON AIR hiện tại.
     */
    private void disconnectUsbCapture(String statusMessage) {
        if (!usbCaptureConnected && !usbCaptureOverlayActive) {
            return;
        }

        usbCaptureConnected = false;
        usbCaptureOverlayActive = false;
        usbCaptureGeneration++;
        usbCaptureStreamReady = false;
        usbCaptureShowPending = false;
        usbCaptureWarmingUp = false;
        updateCaptureButtonAppearance();

        if (usbCapturePlayer != null) {
            usbCapturePlayer.controls().stop();
        }

        if (usbCaptureOverlayFrame != null) {
            SwingUtilities.invokeLater(() -> {
                usbCaptureOverlayFrame.setAlwaysOnTop(false);
                usbCaptureOverlayFrame.setVisible(false);
            });
        }

        if (statusMessage != null) {
            setStatus(statusMessage);
        }
    }

    /**
     * Đồng bộ lại vị trí/kích thước + đưa overlay USB CAPTURE lên trên
     * cùng mỗi khi nativeOutputFrame được set bounds / toFront lại (đổi
     * màn hình xuất, hoặc lấy lại foreground sau khi đóng dialog...).
     */
    private void syncUsbCaptureOverlayBounds() {
        if ((!usbCaptureOverlayActive && !usbCaptureConnected)
                || usbCaptureOverlayFrame == null
                || nativeOutputFrame == null) {
            return;
        }

        Rectangle overlayBounds = nativeOutputFrame.getBounds();

        SwingUtilities.invokeLater(() -> {
            if (!usbCaptureOverlayFrame.getBounds().equals(overlayBounds)) {
                usbCaptureOverlayFrame.setBounds(overlayBounds);
            }
            if (usbCaptureOverlayActive) {
                usbCaptureOverlayFrame.setAlwaysOnTop(true);
                usbCaptureOverlayFrame.toFront();
            } else {
                // READY: vẫn nằm dưới output frame, không hiện.
                usbCaptureOverlayFrame.setAlwaysOnTop(false);
                usbCaptureOverlayFrame.toBack();
            }
        });
    }

    /**
     * Đổi nhãn + style của nút USB CAPTURE theo 1 trong 3 trạng thái:
     * - TẮT hẳn (chưa kết nối): "USB CAPTURE", không viền.
     * - READY (đã kết nối, chưa hiện lên PROGRAM OUTPUT): viền vàng.
     * - ACTIVE (đã kết nối và đang hiện lên PROGRAM OUTPUT): viền xanh.
     */
    private void updateCaptureButtonAppearance() {
        if (captureButton == null) {
            return;
        }

        captureButton.getStyleClass().removeAll(
                "capture-button-active",
                "capture-button-ready"
        );

        if (usbCaptureOverlayActive) {
            String deviceLabel =
                    truncateForButtonLabel(selectedUsbCaptureDeviceName, 16);
            captureButton.setText("T\n" + deviceLabel + " : ACTIVE");
            captureButton.getStyleClass().add("capture-button-active");
            // Khung viền báo hiệu USB CAPTURE đang hiện lên PROGRAM OUTPUT.
            captureButton.setStyle(CAPTURE_BUTTON_ACTIVE_STYLE);
            captureButton.setTooltip(
                    new Tooltip(
                            "USB CAPTURE source: " + selectedUsbCaptureDeviceName
                                    + " (showing on PROGRAM OUTPUT — press T to hide)"
                    )
            );
        } else if (usbCaptureConnected) {
            String deviceLabel =
                    truncateForButtonLabel(selectedUsbCaptureDeviceName, 16);
            captureButton.setText(
                    "T\n" + deviceLabel
                            + (usbCaptureStreamReady
                            ? " : READY "
                            : (usbCaptureShowPending
                            ? " : WAITING..."
                            : " : STARTING...")));
            captureButton.getStyleClass().add("capture-button-ready");
            // Khung viền báo hiệu USB CAPTURE đã kết nối, đang chờ BẬT.
            captureButton.setStyle(CAPTURE_BUTTON_READY_STYLE);
            captureButton.setTooltip(
                    new Tooltip(
                            "USB CAPTURE source: " + selectedUsbCaptureDeviceName
                                    + " (connected — press T to show)"
                    )
            );
        } else {
            captureButton.setText("T\nUSB CAPTURE");
            captureButton.setStyle("");
            captureButton.setTooltip(null);
        }
    }

    private void playUsbAudioOnly() {
        stopAndReleaseExternalAudio();
        externalAudioPlayer = outputVlcFactory
                .mediaPlayers()
                .newMediaPlayer();
        externalAudioPlayer.audio().setVolume(getGeneralVolume());

        boolean started = externalAudioPlayer.media().play(
                "dshow://",
                ":dshow-vdev=none",
                ":dshow-adev=" + CAPTURE_AUDIO_DEVICE,
                ":live-caching=100"
        );

        setStatus(
                started
                        ? "Playing from USB Audio Capture."
                        : "Unable to open USB Audio Capture"
        );
    }

    private void configureVisualSlotDrop(
            Button button,
            int slotIndex,
            String key
    ) {
        button.setOnDragOver(event -> {
            if (firstMatchingFile(event.getDragboard(), true) != null) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        button.setOnDragEntered(event ->
                button.getStyleClass().add("drop-target")
        );

        button.setOnDragExited(event ->
                button.getStyleClass().remove("drop-target")
        );

        button.setOnDragDropped(event -> {
            File file = firstMatchingFile(event.getDragboard(), true);
            boolean success = file != null;

            if (success) {
                visualSlots.set(slotIndex, file);
                button.setText(key + "\n" + file.getName());
                loadMediaToPreview(file, slotIndex);
            }

            button.getStyleClass().remove("drop-target");
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void configureAudioSlotDrop(
            Button button,
            int slotIndex,
            String key
    ) {
        button.setOnDragOver(event -> {
            if (firstMatchingFile(event.getDragboard(), false) != null) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        button.setOnDragEntered(event ->
                button.getStyleClass().add("drop-target")
        );

        button.setOnDragExited(event ->
                button.getStyleClass().remove("drop-target")
        );

        button.setOnDragDropped(event -> {
            File file = firstMatchingFile(event.getDragboard(), false);
            boolean success = file != null;

            if (success) {
                audioSlots.set(slotIndex, file);
                button.setText(key + "  " + file.getName());
                prepareAudio(file, slotIndex);
            }

            button.getStyleClass().remove("drop-target");
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private File firstMatchingFile(Dragboard dragboard, boolean visual) {
        if (!dragboard.hasFiles()) {
            return null;
        }

        return dragboard.getFiles()
                .stream()
                .filter(file -> visual
                        ? isVideoFile(file) || isImageFile(file)
                        : isAudioFile(file)
                )
                .findFirst()
                .orElse(null);
    }

    private String defaultVisualSlotLabel(int slotIndex) {
        return "QWER".charAt(slotIndex)
                + "\nVIDEO / IMAGE "
                + (slotIndex + 1);
    }

    private String defaultAudioSlotLabel(int slotIndex) {
        return "ASDFG".charAt(slotIndex)
                + "  AUDIO "
                + (slotIndex + 1);
    }

    /**
     * Đưa Slot hình/video về trạng thái trống, giống như Preview khi chưa
     * chọn nội dung. Được gọi ngay khi nội dung của Slot này đã được TAKE
     * lên ON AIR, để Slot không giữ lại nội dung đã phát.
     */
    private void resetVisualSlot(int slotIndex) {
        visualSlots.set(slotIndex, null);

        Button button = visualSlotButtons[slotIndex];
        if (button != null) {
            button.setText(defaultVisualSlotLabel(slotIndex));
        }
    }

    /**
     * Tương tự resetVisualSlot(), áp dụng cho Slot audio.
     */
    private void resetAudioSlot(int slotIndex) {
        audioSlots.set(slotIndex, null);

        Button button = audioSlotButtons[slotIndex];
        if (button != null) {
            button.setText(defaultAudioSlotLabel(slotIndex));
        }
    }

    private void handleVisualSlot(int slotIndex, Button button) {
        File file = visualSlots.get(slotIndex);

        if (file == null) {
            file = createVisualChooser(
                    "Assign video / image to Slot " + (slotIndex + 1)
            ).showOpenDialog(primaryStage);

            if (file != null) {
                visualSlots.set(slotIndex, file);
                button.setText(
                        "QWER".charAt(slotIndex)
                                + "\n"
                                + file.getName()
                );
            }
        }

        if (file != null) {
            loadMediaToPreview(file, slotIndex);
        }
    }

    private void handleAudioSlot(int slotIndex, Button button) {
        File file = audioSlots.get(slotIndex);

        if (file == null) {
            file = createAudioChooser(
                    "Assign audio to Slot " + (slotIndex + 1)
            ).showOpenDialog(primaryStage);

            if (file != null) {
                audioSlots.set(slotIndex, file);
                button.setText(
                        "ASDFG".charAt(slotIndex)
                                + "  "
                                + file.getName()
                );
            }
        }

        if (file != null) {
            prepareAudio(file, slotIndex);
        }
    }

    private void openMediaFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select media folder");

        File directory = chooser.showDialog(primaryStage);
        if (directory == null) {
            return;
        }

        File[] files = directory.listFiles(
                file -> file.isFile() && isSupportedMedia(file)
        );

        folderListView.getItems().clear();
        if (files != null) {
            folderListView.getItems().addAll(files);
        }

        setStatus(
                "Opened "
                        + directory.getName()
                        + ": "
                        + folderListView.getItems().size()
                        + " file"
        );
    }

    private void loadFileFromFolder(File file) {
        if (isVideoFile(file) || isImageFile(file)) {
            loadMediaToPreview(file);
        } else if (isAudioFile(file)) {
            prepareAudio(file);
        }
    }

    private void handleKeyboardShortcut(KeyEvent event) {
        if (event.getTarget() instanceof TextInputControl) {
            return;
        }

        switch (event.getCode()) {
            case Q -> activateVisualSlot(0);
            case W -> activateVisualSlot(1);
            case E -> activateVisualSlot(2);
            case R -> activateVisualSlot(3);
            case A -> activateAudioSlot(0);
            case S -> activateAudioSlot(1);
            case D -> activateAudioSlot(2);
            case F -> activateAudioSlot(3);
            case G -> activateAudioSlot(4);
            case ENTER -> takePreviewToOnAir();
            case T -> toggleUsbCaptureOverlay();
            default -> {
                return;
            }
        }

        event.consume();
    }

    private void activateVisualSlot(int slotIndex) {
        File file = visualSlots.get(slotIndex);

        if (file == null) {
            setStatus("Visual Slot " + (slotIndex + 1) + " is not assigned");
            return;
        }

        loadMediaToPreview(file, slotIndex);
    }

    private void activateAudioSlot(int slotIndex) {
        File file = audioSlots.get(slotIndex);

        if (file == null) {
            setStatus("Audio Slot " + (slotIndex + 1) + " is not assigned");
            return;
        }

        prepareAudio(file, slotIndex);
    }

    private FileChooser createVisualChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Video / Image",
                        "*.mp4", "*.m4v", "*.mov", "*.flv",
                        "*.mkv", "*.avi", "*.webm",
                        "*.png", "*.jpg", "*.jpeg", "*.gif",
                        "*.bmp", "*.webp"
                )
        );
        return chooser;
    }

    private FileChooser createAudioChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Audio",
                        "*.mp3", "*.wav", "*.aac",
                        "*.m4a", "*.flac", "*.ogg"
                )
        );
        return chooser;
    }

    private boolean isSupportedMedia(File file) {
        return isVideoFile(file)
                || isImageFile(file)
                || isAudioFile(file);
    }

    private boolean isImageFile(File file) {
        return hasExtension(
                file,
                ".png", ".jpg", ".jpeg",
                ".gif", ".bmp", ".webp"
        );
    }

    private boolean isVideoFile(File file) {
        return hasExtension(
                file,
                ".mp4", ".m4v", ".mov", ".flv",
                ".mkv", ".avi", ".webm"
        );
    }

    private boolean isAudioFile(File file) {
        return hasExtension(
                file,
                ".mp3", ".wav", ".aac",
                ".m4a", ".flac", ".ogg"
        );
    }

    private boolean hasExtension(File file, String... extensions) {
        if (file == null) {
            return false;
        }

        String fileName = file.getName().toLowerCase(Locale.ROOT);

        for (String extension : extensions) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    private int getGeneralVolume() {
        return (int) Math.round(generalVolumeSlider.getValue() * 100);
    }

    private void updateGeneralVolume() {
        int volume = getGeneralVolume();

        // Preview không có audio output nên không được chỉnh mute hay volume.
        if (onAirPlayer != null && !onAirIsImage) {
            onAirPlayer.audio().setVolume(volume);
        }

        if (externalAudioPlayer != null) {
            externalAudioPlayer.audio().setVolume(volume);
        }
    }

    private void resetVideoProgress() {
        videoProgressSlider.setMin(0);
        videoProgressSlider.setMax(1);
        videoProgressSlider.setValue(0);
        videoProgressSlider.setDisable(true);
    }

    private void resetAudioProgress() {
        audioProgressSlider.setMin(0);
        audioProgressSlider.setMax(1);
        audioProgressSlider.setValue(0);
        audioProgressSlider.setDisable(true);
    }

    private void setLiveState(boolean live) {
        liveIndicator.getStyleClass().removeAll(
                "online-indicator",
                "offline-indicator"
        );

        liveIndicator.setText(live ? "● ON AIR" : "● OFFLINE");
        liveIndicator.getStyleClass().add(
                live ? "online-indicator" : "offline-indicator"
        );
    }

    private void setStateLabel(Label label, String text, boolean ready) {
        label.setText(text);
        label.getStyleClass().removeAll("ready-label", "empty-label");
        label.getStyleClass().add(ready ? "ready-label" : "empty-label");
    }

    private static Label createStateLabel(String text, boolean ready) {
        Label label = new Label(text);
        label.getStyleClass().add(ready ? "ready-label" : "empty-label");
        return label;
    }

    private Label createSectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private Button createCompactButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("compact-button");
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(title);
        alert.setContentText(
                message == null ? "Unknown error" : message
        );
        alert.showAndWait();
    }

    private void disposeAllPlayers() {
        closeOutputScreen();
        stopPreviewResolutionCheck();
        stopOnAirThumbnailRefresh();

        if (nativeOutputFrame != null) {
            JFrame frameToDispose = nativeOutputFrame;
            nativeOutputFrame = null;
            outputCanvas = null;
            SwingUtilities.invokeLater(frameToDispose::dispose);
        }

        stopAndReleaseExternalAudio();

        if (previewPlayer != null) {
            previewPlayer.controls().stop();
            previewPlayer.release();
            previewPlayer = null;
        }

        if (onAirPlayer != null) {
            onAirPlayer.controls().stop();
            onAirPlayer.release();
            onAirPlayer = null;
        }

        usbCaptureConnected = false;
        usbCaptureOverlayActive = false;

        if (usbCaptureOverlayFrame != null) {
            JFrame overlayFrameToDispose = usbCaptureOverlayFrame;
            usbCaptureOverlayFrame = null;
            usbCaptureCanvas = null;
            SwingUtilities.invokeLater(overlayFrameToDispose::dispose);
        }

        if (usbCapturePlayer != null) {
            usbCapturePlayer.controls().stop();
            usbCapturePlayer.release();
            usbCapturePlayer = null;
        }

        if (previewVlcFactory != null) {
            previewVlcFactory.release();
            previewVlcFactory = null;
        }

        if (outputVlcFactory != null) {
            outputVlcFactory.release();
            outputVlcFactory = null;
        }

        if (usbCaptureVlcFactory != null) {
            usbCaptureVlcFactory.release();
            usbCaptureVlcFactory = null;
        }
    }

    private static final class ScreenOption {

        private final Screen screen;

        private final int screenNumber;

        private final boolean studioCenterScreen;

        private final boolean primaryScreen;

        // Toạ độ AWT "thật" (java.awt.GraphicsDevice) của đúng màn hình vật lý
        // này. nativeOutputFrame là JFrame (AWT/Swing), trong khi 'screen' ở
        // trên là javafx.stage.Screen. Trên Windows, khi các màn hình có tỉ lệ
        // DPI khác nhau (rất hay gặp khi cắm từ 3 màn hình trở lên), JavaFX và
        // AWT có thể quy đổi toạ độ/kích thước màn hình phụ theo hai cách KHÁC
        // NHAU so với màn hình chính, khiến Rectangle2D lấy từ Screen.getBounds()
        // không còn khớp với toạ độ AWT thật -> setBounds() cho JFrame bị lệch
        // hoặc rơi sang đúng khu vực của MỘT màn hình khác. Field này (có thể
        // null nếu không dò được thiết bị AWT tương ứng) cho phép dùng thẳng
        // Rectangle của GraphicsDevice để định vị nativeOutputFrame, đảm bảo
        // luôn hiển thị đúng màn hình đã chọn. Xem findMatchingAwtBounds().
        private final Rectangle awtBounds;

        private ScreenOption(
                Screen screen,
                int screenNumber,
                boolean studioCenterScreen,
                boolean primaryScreen,
                Rectangle awtBounds
        ) {
            this.screen = screen;
            this.screenNumber = screenNumber;
            this.studioCenterScreen = studioCenterScreen;
            this.primaryScreen = primaryScreen;
            this.awtBounds = awtBounds;
        }

        public Rectangle getAwtBounds() {
            return awtBounds;
        }

        public Screen getScreen() {
            return screen;
        }

        public int getScreenNumber() {
            return screenNumber;
        }

        public boolean isStudioCenterScreen() {
            return studioCenterScreen;
        }

        public boolean isPrimaryScreen() {
            return primaryScreen;
        }

        public String getResolutionText() {
            Rectangle2D bounds = screen.getBounds();

            return (int) bounds.getWidth()
                    + " × "
                    + (int) bounds.getHeight();
        }

        public String getPositionText() {
            Rectangle2D bounds = screen.getBounds();

            return "X: "
                    + (int) bounds.getMinX()
                    + "   Y: "
                    + (int) bounds.getMinY();
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder();

            text.append("Screen ")
                    .append(screenNumber)
                    .append(" - ")
                    .append(getResolutionText());

            if (studioCenterScreen) {
                text.append(" (Studio Center)");
            }

            if (primaryScreen) {
                text.append(" (Primary)");
            }

            return text.toString();
        }
    }

    private void showScreenSelectionDialog() {
        List<Screen> screens = Screen.getScreens();

        if (screens.isEmpty()) {
            setStatus("Screen not found");
            return;
        }

        Screen studioCenterScreen =
                findStudioCenterScreen();

        List<ScreenOption> allOptions =
                createScreenOptions(
                        screens,
                        studioCenterScreen
                );

        /*
         * Chỉ hiển thị những màn hình KHÔNG có app control (Studio Center)
         * đang chạy trên đó, để tránh chọn nhầm màn hình đang dùng để
         * điều khiển làm màn hình PROGRAM OUTPUT.
         */
        List<ScreenOption> options =
                filterSelectableScreenOptions(allOptions);

        if (options.isEmpty()) {
            setStatus(
                    "No available screen (every screen currently has app control on it)"
            );
            return;
        }

        Dialog<ScreenOption> dialog =
                new Dialog<>();

        dialog.setTitle(
                "Select Output Screen"
        );

        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(false);

        DialogPane dialogPane =
                dialog.getDialogPane();

        dialogPane.getStyleClass().add(
                "screen-dialog"
        );

        dialogPane.getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL
        );

        ComboBox<ScreenOption> screenComboBox =
                createScreenComboBox(options);

        ScreenOption initialOption =
                findInitialScreenOption(options);

        screenComboBox.setValue(initialOption);

        Label warningLabel =
                createScreenWarningLabel();

        updateScreenWarning(
                warningLabel,
                initialOption
        );

        screenComboBox
                .valueProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                updateScreenWarning(
                                        warningLabel,
                                        newValue
                                )
                );

        VBox content =
                createScreenDialogContent(
                        screenComboBox,
                        warningLabel
                );

        dialogPane.setContent(content);

        /*
         * Không đặt chiều cao cố định cho Dialog.
         * JavaFX sẽ tính dựa trên nội dung thực tế.
         */
        dialogPane.setMinWidth(620);
        dialogPane.setPrefWidth(680);
        dialogPane.setMaxWidth(720);

        dialogPane.setMinHeight(
                Region.USE_PREF_SIZE
        );

        dialogPane.setPrefHeight(
                Region.USE_COMPUTED_SIZE
        );

        dialogPane.setMaxHeight(
                Region.USE_PREF_SIZE
        );



        Node okButton =
                dialogPane.lookupButton(
                        ButtonType.OK
                );

        okButton.disableProperty().bind(
                screenComboBox
                        .valueProperty()
                        .isNull()
        );

        dialog.setResultConverter(
                buttonType -> {
                    if (buttonType == ButtonType.OK) {
                        return screenComboBox.getValue();
                    }

                    return null;
                }
        );

        /*
         * Điều chỉnh Dialog sau khi Window đã được tạo.
         */
        dialog.setOnShown(event ->
                resizeAndCenterScreenDialog(dialog)
        );

        try {
            dialog.showAndWait().ifPresent(
                    this::openOutputScreen
            );
        } finally {
            Platform.runLater(
                    this::restoreOutputScreenWindow
            );
        }
    }

    private List<ScreenOption> createScreenOptions(
            List<Screen> screens,
            Screen studioCenterScreen
    ) {
        List<ScreenOption> options =
                new ArrayList<>();

        GraphicsDevice[] awtDevices =
                GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getScreenDevices();

        for (int index = 0;
             index < screens.size();
             index++) {

            Screen screen = screens.get(index);

            ScreenOption option =
                    new ScreenOption(
                            screen,
                            index + 1,
                            screen.equals(
                                    studioCenterScreen
                            ),
                            screen.equals(
                                    Screen.getPrimary()
                            ),
                            findMatchingAwtBounds(
                                    screen,
                                    awtDevices
                            )
                    );

            options.add(option);
        }

        return options;
    }

    /**
     * Tìm Rectangle AWT (java.awt.GraphicsDevice) tương ứng với một
     * javafx.stage.Screen, để dùng khi đặt vị trí cho nativeOutputFrame
     * (là JFrame - AWT/Swing), thay vì quy đổi trực tiếp từ Rectangle2D
     * của JavaFX (toAwtRectangle). Lý do: khi có từ 3 màn hình trở lên,
     * đặc biệt nếu các màn hình có tỉ lệ DPI (scale) khác nhau, JavaFX và
     * AWT có thể tính toạ độ màn hình phụ SAI KHÁC NHAU so với màn hình
     * chính -> nếu chỉ quy đổi Rectangle2D của JavaFX rồi setBounds() cho
     * JFrame, cửa sổ PROGRAM OUTPUT có thể mở lệch, méo kích thước, hoặc
     * hiện nhầm sang màn hình khác dù đã chọn đúng "Screen X" trong danh
     * sách.
     *
     * Vì hai danh sách (Screen.getScreens() và getScreenDevices()) không
     * đảm bảo cùng thứ tự tuyệt đối trong mọi trường hợp, việc so khớp
     * dựa trên chỉ số (index) đơn thuần là không an toàn. Thay vào đó,
     * hàm này so khớp theo VỊ TRÍ TƯƠNG ĐỐI (điểm gốc trên-trái, quy đổi
     * theo tỉ lệ của từng bên) - màn hình AWT có điểm gốc gần nhất với
     * điểm gốc (đã quy đổi) của Screen JavaFX sẽ được coi là cùng một
     * màn hình vật lý.
     */
    private Rectangle findMatchingAwtBounds(
            Screen screen,
            GraphicsDevice[] awtDevices
    ) {
        if (awtDevices == null || awtDevices.length == 0) {
            return null;
        }

        // Nếu số lượng khớp nhau (trường hợp phổ biến nhất), và chỉ có
        // MỘT màn hình, không cần so khớp gì thêm.
        if (awtDevices.length == 1) {
            return awtDevices[0]
                    .getDefaultConfiguration()
                    .getBounds();
        }

        List<Screen> allScreens = Screen.getScreens();
        int screenIndex = allScreens.indexOf(screen);

        if (screenIndex < 0) {
            return null;
        }

        Rectangle2D fxBounds = screen.getBounds();

        // Tỉ lệ tương đối của điểm gốc JavaFX so với "hộp bao" chứa toàn
        // bộ các Screen JavaFX - dùng để so khớp bất kể JavaFX/AWT có
        // đang biểu diễn toạ độ theo hệ số DPI khác nhau hay không.
        double minFxX = Double.MAX_VALUE;
        double minFxY = Double.MAX_VALUE;
        double maxFxX = -Double.MAX_VALUE;
        double maxFxY = -Double.MAX_VALUE;

        for (Screen candidate : allScreens) {
            Rectangle2D bounds = candidate.getBounds();
            minFxX = Math.min(minFxX, bounds.getMinX());
            minFxY = Math.min(minFxY, bounds.getMinY());
            maxFxX = Math.max(maxFxX, bounds.getMaxX());
            maxFxY = Math.max(maxFxY, bounds.getMaxY());
        }

        double fxSpanX = Math.max(1, maxFxX - minFxX);
        double fxSpanY = Math.max(1, maxFxY - minFxY);

        double fxRelX = (fxBounds.getMinX() - minFxX) / fxSpanX;
        double fxRelY = (fxBounds.getMinY() - minFxY) / fxSpanY;

        double minAwtX = Double.MAX_VALUE;
        double minAwtY = Double.MAX_VALUE;
        double maxAwtX = -Double.MAX_VALUE;
        double maxAwtY = -Double.MAX_VALUE;

        Rectangle[] awtBoundsList = new Rectangle[awtDevices.length];

        for (int i = 0; i < awtDevices.length; i++) {
            Rectangle bounds =
                    awtDevices[i]
                            .getDefaultConfiguration()
                            .getBounds();

            awtBoundsList[i] = bounds;

            minAwtX = Math.min(minAwtX, bounds.getMinX());
            minAwtY = Math.min(minAwtY, bounds.getMinY());
            maxAwtX = Math.max(maxAwtX, bounds.getMaxX());
            maxAwtY = Math.max(maxAwtY, bounds.getMaxY());
        }

        double awtSpanX = Math.max(1, maxAwtX - minAwtX);
        double awtSpanY = Math.max(1, maxAwtY - minAwtY);

        Rectangle bestMatch = null;
        double bestDistance = Double.MAX_VALUE;

        for (Rectangle bounds : awtBoundsList) {
            double awtRelX = (bounds.getMinX() - minAwtX) / awtSpanX;
            double awtRelY = (bounds.getMinY() - minAwtY) / awtSpanY;

            double distance =
                    Math.abs(awtRelX - fxRelX)
                            + Math.abs(awtRelY - fxRelY);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = bounds;
            }
        }

        return bestMatch;
    }

    /**
     * Lọc bỏ những màn hình đang có app control (Studio Center) chạy trên đó,
     * vì màn hình đó không thể/không nên dùng làm PROGRAM OUTPUT.
     */
    private List<ScreenOption> filterSelectableScreenOptions(
            List<ScreenOption> options
    ) {
        List<ScreenOption> selectable = new ArrayList<>();

        for (ScreenOption option : options) {
            if (!option.isStudioCenterScreen()) {
                selectable.add(option);
            }
        }

        return selectable;
    }

    private ComboBox<ScreenOption> createScreenComboBox(
            List<ScreenOption> options
    ) {
        ComboBox<ScreenOption> comboBox =
                new ComboBox<>(
                        FXCollections
                                .observableArrayList(
                                        options
                                )
                );

        comboBox.setMaxWidth(
                Double.MAX_VALUE
        );

        comboBox.setPrefWidth(620);
        comboBox.setVisibleRowCount(
                Math.min(options.size(), 5)
        );

        /*
         * Cell đầy đủ chỉ dùng trong danh sách xổ xuống.
         */
        comboBox.setCellFactory(
                listView ->
                        createScreenListCell()
        );

        /*
         * Cell gọn dùng cho màn hình đã chọn.
         * Không dùng cell ba dòng tại đây.
         */
        comboBox.setButtonCell(
                createSelectedScreenCell()
        );

        return comboBox;
    }

    private ListCell<ScreenOption>
    createSelectedScreenCell() {

        return new ListCell<>() {

            @Override
            protected void updateItem(
                    ScreenOption option,
                    boolean empty
            ) {
                super.updateItem(option, empty);

                if (empty || option == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label monitorIcon =
                        new Label("▣");

                monitorIcon
                        .getStyleClass()
                        .add("selected-screen-icon");

                Label nameLabel =
                        new Label(
                                "Screen "
                                        + option
                                        .getScreenNumber()
                        );

                nameLabel
                        .getStyleClass()
                        .add("selected-screen-name");

                Label resolutionLabel =
                        new Label(
                                option.getResolutionText()
                        );

                resolutionLabel
                        .getStyleClass()
                        .add("selected-screen-resolution");

                Region spacer = new Region();

                HBox.setHgrow(
                        spacer,
                        Priority.ALWAYS
                );

                HBox selectedRow =
                        new HBox(
                                10,
                                monitorIcon,
                                nameLabel,
                                resolutionLabel,
                                spacer
                        );

                selectedRow.setAlignment(
                        Pos.CENTER_LEFT
                );

                selectedRow.setMinHeight(42);
                selectedRow.setPrefHeight(42);
                selectedRow.setMaxHeight(42);

                setText(null);
                setGraphic(selectedRow);
            }
        };
    }

    private ListCell<ScreenOption>
    createScreenListCell() {

        return new ListCell<>() {

            @Override
            protected void updateItem(
                    ScreenOption option,
                    boolean empty
            ) {
                super.updateItem(option, empty);

                if (empty || option == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label monitorIcon =
                        new Label("▣");

                monitorIcon
                        .getStyleClass()
                        .add("screen-option-icon");

                Label nameLabel =
                        new Label(
                                "Screen "
                                        + option
                                        .getScreenNumber()
                        );

                nameLabel
                        .getStyleClass()
                        .add("screen-option-name");

                Label detailLabel =
                        new Label(
                                option.getResolutionText()
                                        + "  •  "
                                        + option.getPositionText()
                        );

                detailLabel
                        .getStyleClass()
                        .add("screen-option-detail");

                HBox badgeBox =
                        createScreenBadgeBox(option);

                VBox informationBox =
                        new VBox(
                                2,
                                nameLabel,
                                detailLabel,
                                badgeBox
                        );

                informationBox.setMaxWidth(
                        Double.MAX_VALUE
                );

                HBox.setHgrow(
                        informationBox,
                        Priority.ALWAYS
                );

                HBox graphic =
                        new HBox(
                                10,
                                monitorIcon,
                                informationBox
                        );

                graphic.setAlignment(
                        Pos.CENTER_LEFT
                );

                graphic.setPadding(
                        new Insets(5)
                );

                graphic.setMinHeight(64);
                graphic.setPrefHeight(64);
                graphic.setMaxHeight(64);

                setText(null);
                setGraphic(graphic);
            }
        };
    }

    private HBox createScreenBadgeBox(
            ScreenOption option
    ) {
        HBox badgeBox = new HBox(6);

        if (option.isStudioCenterScreen()) {
            Label studioBadge =
                    new Label("STUDIO CENTER");

            studioBadge
                    .getStyleClass()
                    .add("studio-screen-badge");

            badgeBox
                    .getChildren()
                    .add(studioBadge);
        }

        if (option.isPrimaryScreen()) {
            Label primaryBadge =
                    new Label("PRIMARY");

            primaryBadge
                    .getStyleClass()
                    .add("primary-screen-badge");

            badgeBox
                    .getChildren()
                    .add(primaryBadge);
        }

        return badgeBox;
    }

    private VBox createScreenDialogContent(
            ComboBox<ScreenOption> screenComboBox,
            Label warningLabel
    ) {
        Label iconLabel =
                new Label("▣");

        iconLabel
                .getStyleClass()
                .add("screen-dialog-icon");

        Label titleLabel =
                new Label(
                        "PROGRAM OUTPUT (Win + p) -> Extend"
                );

        titleLabel
                .getStyleClass()
                .add("screen-dialog-title");

        Label descriptionLabel = new Label("Select the screen to display ON AIR content");

        descriptionLabel.setWrapText(true);

        descriptionLabel
                .getStyleClass()
                .add(
                        "screen-dialog-description"
                );

        VBox titleBox =
                new VBox(
                        3,
                        titleLabel,
                        descriptionLabel
                );

        HBox headerBox =
                new HBox(
                        12,
                        iconLabel,
                        titleBox
                );

        headerBox.setAlignment(
                Pos.CENTER_LEFT
        );

        Label fieldLabel =
                new Label(
                        "OUTPUT SCREEN"
                );

        fieldLabel
                .getStyleClass()
                .add(
                        "screen-dialog-field-label"
                );

        Separator separator =
                new Separator();

        VBox content =
                new VBox(
                        12,
                        headerBox,
                        separator,
                        fieldLabel,
                        screenComboBox,
                        warningLabel
                );

        content.setMinHeight(
                Region.USE_PREF_SIZE
        );

        content.setPrefHeight(
                Region.USE_COMPUTED_SIZE
        );

        content.setMaxHeight(
                Region.USE_PREF_SIZE
        );

        content.setPadding(
                new Insets(8)
        );

        return content;
    }

    private Label createScreenWarningLabel() {
        Label warningLabel = new Label();

        warningLabel.setWrapText(true);
        warningLabel.setMaxWidth(
                Double.MAX_VALUE
        );

        warningLabel
                .getStyleClass()
                .add(
                        "screen-dialog-warning"
                );

        return warningLabel;
    }

    private void resizeAndCenterScreenDialog(
            Dialog<?> dialog
    ) {
        if (!(dialog.getDialogPane()
                .getScene()
                .getWindow() instanceof Stage dialogStage)) {
            return;
        }

        Rectangle2D visualBounds =
                findStudioCenterScreen()
                        .getVisualBounds();

        /*
         * Tính lại đúng kích thước nội dung.
         */
        dialogStage.sizeToScene();

        double maximumWidth =
                visualBounds.getWidth() * 0.80;

        double maximumHeight =
                visualBounds.getHeight() * 0.82;

        if (dialogStage.getWidth()
                > maximumWidth) {
            dialogStage.setWidth(
                    maximumWidth
            );
        }

        if (dialogStage.getHeight()
                > maximumHeight) {
            dialogStage.setHeight(
                    maximumHeight
            );
        }

        double dialogX =
                visualBounds.getMinX()
                        + (
                        visualBounds.getWidth()
                                - dialogStage.getWidth()
                ) / 2;

        double dialogY =
                visualBounds.getMinY()
                        + (
                        visualBounds.getHeight()
                                - dialogStage.getHeight()
                ) / 2;

        dialogStage.setX(dialogX);
        dialogStage.setY(dialogY);
    }



    private ListCell<ScreenOption> createScreenOptionCell() {
        return new ListCell<>() {

            @Override
            protected void updateItem(
                    ScreenOption option,
                    boolean empty
            ) {
                super.updateItem(option, empty);

                if (empty || option == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label monitorIcon = new Label("▣");

                monitorIcon.getStyleClass().add(
                        "screen-option-icon"
                );

                Label nameLabel = new Label(
                        "Screen "
                                + option.getScreenNumber()
                );

                nameLabel.getStyleClass().add(
                        "screen-option-name"
                );

                Label detailLabel = new Label(
                        option.getResolutionText()
                                + "   •   "
                                + option.getPositionText()
                );

                detailLabel.getStyleClass().add(
                        "screen-option-detail"
                );

                HBox badgeBox = new HBox(6);

                if (option.isStudioCenterScreen()) {
                    Label studioBadge = new Label(
                            "STUDIO CENTER"
                    );

                    studioBadge.getStyleClass().add(
                            "studio-screen-badge"
                    );

                    badgeBox.getChildren().add(
                            studioBadge
                    );
                }

                if (option.isPrimaryScreen()) {
                    Label primaryBadge = new Label(
                            "PRIMARY"
                    );

                    primaryBadge.getStyleClass().add(
                            "primary-screen-badge"
                    );

                    badgeBox.getChildren().add(
                            primaryBadge
                    );
                }

                VBox informationBox = new VBox(
                        3,
                        nameLabel,
                        detailLabel,
                        badgeBox
                );

                informationBox.setMaxWidth(
                        Double.MAX_VALUE
                );

                HBox.setHgrow(
                        informationBox,
                        Priority.ALWAYS
                );

                HBox graphic = new HBox(
                        12,
                        monitorIcon,
                        informationBox
                );

                graphic.setAlignment(
                        Pos.CENTER_LEFT
                );

                graphic.setPadding(
                        new Insets(5)
                );

                setText(null);
                setGraphic(graphic);
            }
        };
    }


    private ScreenOption findInitialScreenOption(
            List<ScreenOption> options
    ) {
        if (selectedOutputScreen != null) {
            for (ScreenOption option : options) {
                if (option.getScreen().equals(
                        selectedOutputScreen
                )) {
                    return option;
                }
            }
        }

        /*
         * Ưu tiên màn hình không chứa Studio Center.
         * Cách này gần giống cách PowerPoint đề xuất
         * màn hình trình chiếu.
         */
        for (ScreenOption option : options) {
            if (!option.isStudioCenterScreen()) {
                return option;
            }
        }

        return options.get(0);
    }

    private void updateScreenWarning(
            Label warningLabel,
            ScreenOption option
    ) {
        if (option == null) {
            warningLabel.setText(
                    "No screen selected"
            );

            warningLabel.getStyleClass().removeAll(
                    "screen-warning-normal",
                    "screen-warning-danger"
            );

            return;
        }

        warningLabel.getStyleClass().removeAll(
                "screen-warning-normal",
                "screen-warning-danger"
        );

        if (option.isStudioCenterScreen()) {
            warningLabel.setText("This screen is currently displaying Studio Center");

            warningLabel.getStyleClass().add(
                    "screen-warning-danger"
            );
        } else {
            warningLabel.setText(
                    "This screen is ready to use as PROGRAM OUTPUT"
            );

            warningLabel.getStyleClass().add(
                    "screen-warning-normal"
            );
        }
    }

    private Screen findStudioCenterScreen() {
        if (primaryStage == null) {
            return Screen.getPrimary();
        }

        double stageX = primaryStage.getX();
        double stageY = primaryStage.getY();
        double stageWidth = primaryStage.getWidth();
        double stageHeight = primaryStage.getHeight();

        List<Screen> intersectingScreens =
                Screen.getScreensForRectangle(
                        stageX,
                        stageY,
                        stageWidth,
                        stageHeight
                );

        if (intersectingScreens.isEmpty()) {
            return Screen.getPrimary();
        }

        Screen bestScreen =
                intersectingScreens.get(0);

        double largestArea = -1;

        for (Screen screen : intersectingScreens) {
            double area = calculateIntersectionArea(
                    stageX,
                    stageY,
                    stageWidth,
                    stageHeight,
                    screen.getBounds()
            );

            if (area > largestArea) {
                largestArea = area;
                bestScreen = screen;
            }
        }

        return bestScreen;
    }

    private double calculateIntersectionArea(
            double windowX,
            double windowY,
            double windowWidth,
            double windowHeight,
            Rectangle2D screenBounds
    ) {
        double intersectionWidth = Math.max(
                0,
                Math.min(
                        windowX + windowWidth,
                        screenBounds.getMaxX()
                ) - Math.max(
                        windowX,
                        screenBounds.getMinX()
                )
        );

        double intersectionHeight = Math.max(
                0,
                Math.min(
                        windowY + windowHeight,
                        screenBounds.getMaxY()
                ) - Math.max(
                        windowY,
                        screenBounds.getMinY()
                )
        );

        return intersectionWidth
                * intersectionHeight;
    }

    private void openOutputScreen(
            ScreenOption selectedOption
    ) {
        if (selectedOption == null) {
            setStatus("The selected screen is invalid");
            return;
        }

        if (nativeOutputFrame == null || outputCanvas == null) {
            setStatus("PROGRAM OUTPUT chua san sang (VLC chua khoi tao xong)");
            return;
        }

        selectedOutputScreen =
                selectedOption.getScreen();
        selectedOutputScreenAwtBounds =
                selectedOption.getAwtBounds();

        // Đã chọn màn hình -> nút SELECT SCREEN chuyển xanh,
        // nút TAKE -> ON AIR được mở khoá.
        updateSelectScreenButtonAppearance();
        updateTakeButtonAvailability();

        Rectangle screenBounds =
                selectedOutputScreenAwtBounds != null
                        ? selectedOutputScreenAwtBounds
                        : toAwtRectangle(selectedOutputScreen.getBounds());

        outputScreenOpen = true;

        /*
         * Khong tao lai Stage/ImageView/VideoSurface nhu truoc.
         * Chi dat lai vi tri/kich thuoc cua cua so native va hien no len -
         * VideoSurface da duoc gan co dinh vao onAirPlayer tu luc
         * createNativeOutputWindow() (xem attachVideoSurfaces()).
         */
        SwingUtilities.invokeLater(() -> {
            nativeOutputFrame.setBounds(screenBounds);
            nativeOutputFrame.setAlwaysOnTop(true);
            nativeOutputFrame.setVisible(true);
            nativeOutputFrame.toFront();
            outputCanvas.requestFocusInWindow();
            syncUsbCaptureOverlayBounds();
        });

        Platform.runLater(() -> {
            /*
             * Tra focus cho Studio Center.
             * Khong lien tuc goi toFront() sau doan nay.
             */
            if (!selectedOption.isStudioCenterScreen()
                    && primaryStage != null
                    && primaryStage.isShowing()) {
                primaryStage.requestFocus();
            }
        });

        setStatus(
                "PROGRAM OUTPUT is currently displayed on Screen "
                        + selectedOption.getScreenNumber()
        );
    }

    private Rectangle toAwtRectangle(Rectangle2D screenBounds) {
        return new Rectangle(
                (int) Math.round(screenBounds.getMinX()),
                (int) Math.round(screenBounds.getMinY()),
                (int) Math.round(screenBounds.getWidth()),
                (int) Math.round(screenBounds.getHeight())
        );
    }

    /**
     * Duoc goi sau khi dialog chon man hinh dong (xem showScreenSelectionDialog),
     * de dam bao cua so PROGRAM OUTPUT native van dung vi tri/luon-on-top
     * sau khi nguoi dung tuong tac voi dialog JavaFX.
     */
    private void restoreOutputScreenWindow() {
        if (!outputScreenOpen
                || nativeOutputFrame == null
                || selectedOutputScreen == null) {
            return;
        }

        Rectangle screenBounds =
                selectedOutputScreenAwtBounds != null
                        ? selectedOutputScreenAwtBounds
                        : toAwtRectangle(selectedOutputScreen.getBounds());

        SwingUtilities.invokeLater(() -> {
            if (!outputScreenOpen) {
                return;
            }

            if ((nativeOutputFrame.getExtendedState() & JFrame.ICONIFIED) != 0) {
                nativeOutputFrame.setExtendedState(java.awt.Frame.NORMAL);
            }

            nativeOutputFrame.setBounds(screenBounds);
            nativeOutputFrame.setAlwaysOnTop(true);
            nativeOutputFrame.toFront();
            syncUsbCaptureOverlayBounds();
        });

        /*
         * Tra focus lai cho cua so dieu khien sau khi
         * Output da duoc dua len dung tang hien thi.
         */
        Platform.runLater(() -> {
            if (primaryStage != null
                    && primaryStage.isShowing()) {
                primaryStage.requestFocus();
            }
        });
    }

    private void closeOutputScreen() {
        if (!outputScreenOpen) {
            return;
        }

        outputScreenOpen = false;
        selectedOutputScreen = null;
        selectedOutputScreenAwtBounds = null;

        /*
         * Không còn PROGRAM OUTPUT hợp lệ để USB CAPTURE đè lên (dù đang
         * ACTIVE hay chỉ đang READY/standby) -> ngắt kết nối hẳn, tránh
         * giữ thiết bị USB mở vô ích. Lần BẬT kế tiếp (sau khi chọn lại
         * SELECT SCREEN) sẽ cần chọn/kết nối lại thiết bị như bình thường.
         */
        if (usbCaptureConnected) {
            disconnectUsbCapture(null);
        }

        /*
         * FIX NullPointerException khi đóng app: lambda invokeLater chạy
         * SAU, lúc đó disposeAllPlayers() đã gán nativeOutputFrame = null.
         * Phải chụp tham chiếu vào biến cục bộ TRƯỚC khi đưa vào lambda.
         */
        final JFrame frameToHide = nativeOutputFrame;
        if (frameToHide != null) {
            SwingUtilities.invokeLater(() -> {
                frameToHide.setAlwaysOnTop(false);
                frameToHide.setVisible(false);
            });
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
