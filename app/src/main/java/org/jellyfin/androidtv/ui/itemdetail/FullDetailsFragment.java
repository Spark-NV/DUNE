package org.jellyfin.androidtv.ui.itemdetail;

import static org.koin.java.KoinJavaComponent.inject;

import org.koin.java.KoinJavaComponent;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewKt;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.lifecycle.Lifecycle;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.auth.repository.UserRepository;
import org.jellyfin.androidtv.constant.CustomMessage;
import org.jellyfin.androidtv.constant.QueryType;
import org.jellyfin.androidtv.data.model.ChapterItemInfo;
import org.jellyfin.androidtv.data.model.DataRefreshService;
import org.jellyfin.androidtv.data.model.InfoItem;
import org.jellyfin.androidtv.data.querying.GetAdditionalPartsRequest;
import org.jellyfin.androidtv.data.querying.GetSpecialsRequest;
import org.jellyfin.androidtv.data.querying.GetTrailersRequest;
import org.jellyfin.androidtv.data.repository.CustomMessageRepository;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.databinding.FragmentFullDetailsBinding;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.ClockBehavior;
import org.jellyfin.androidtv.ui.RecordPopup;
import org.jellyfin.androidtv.ui.RecordingIndicatorView;
import org.jellyfin.androidtv.ui.SubtitleManagementPopup;
import org.jellyfin.androidtv.ui.shared.buttons.DetailButton;
import org.jellyfin.androidtv.ui.browsing.BrowsingUtils;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter;
import org.jellyfin.androidtv.ui.livetv.TvManager;
import org.jellyfin.androidtv.ui.navigation.Destinations;
import org.jellyfin.androidtv.ui.navigation.NavigationRepository;
import org.jellyfin.androidtv.ui.playback.MediaManager;
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher;
import org.jellyfin.androidtv.ui.presentation.CardPresenter;
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter;
import org.jellyfin.androidtv.ui.presentation.InfoCardPresenter;
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter;
import org.jellyfin.androidtv.ui.presentation.MyDetailsOverviewRowPresenter;
import org.jellyfin.androidtv.util.CoroutineUtils;
import org.jellyfin.androidtv.util.DateTimeExtensionsKt;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.KeyProcessor;
import org.jellyfin.androidtv.util.MarkdownRenderer;
import org.jellyfin.androidtv.util.PlaybackHelper;
import org.jellyfin.androidtv.util.TimeUtils;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.apiclient.BaseItemUtils;
import org.jellyfin.androidtv.util.apiclient.Response;
import org.jellyfin.androidtv.util.sdk.BaseItemExtensionsKt;
import org.jellyfin.androidtv.util.sdk.TrailerUtils;
import org.jellyfin.androidtv.util.sdk.compat.JavaCompat;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.BaseItemPerson;
import org.jellyfin.sdk.model.api.MediaSourceInfo;
import org.jellyfin.sdk.model.api.MediaStream;
import org.jellyfin.sdk.model.api.MediaType;
import org.jellyfin.sdk.model.api.PersonKind;
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto;
import org.jellyfin.sdk.model.api.UserDto;
import org.jellyfin.sdk.model.serializer.UUIDSerializerKt;

import org.jellyfin.sdk.model.api.DeviceProfile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import kotlin.Lazy;
import kotlinx.serialization.json.Json;
import timber.log.Timber;

public class FullDetailsFragment extends Fragment implements RecordingIndicatorView, View.OnKeyListener {

    private int BUTTON_SIZE;

    DetailButton mResumeButton;
    private DetailButton mVersionsButton;
    DetailButton mPrevButton;
    private DetailButton mRecordButton;
    private DetailButton mRecSeriesButton;
    private DetailButton mSeriesSettingsButton;
    DetailButton mWatchedToggleButton;
    DetailButton mCollectionsButton;
    DetailButton mPlaylistsButton;

    private DisplayMetrics mMetrics;

    protected BaseItemDto mProgramInfo;
    protected SeriesTimerInfoDto mSeriesTimerInfo;
    protected UUID mItemId;
    protected UUID mChannelId;
    protected BaseRowItem mCurrentItem;
    private Instant mLastUpdated;
    public UUID mPrevItemId;

    private RowsSupportFragment mRowsFragment;
    private MutableObjectAdapter<Row> mRowsAdapter;

    private MyDetailsOverviewRowPresenter mDorPresenter;
    private MyDetailsOverviewRow mDetailsOverviewRow;
    private CustomListRowPresenter mListRowPresenter;

    private Handler mLoopHandler = new Handler();
    private Runnable mClockLoop;

    BaseItemDto mBaseItem;

    private ArrayList<MediaSourceInfo> versions;
    private java.util.Map<String, String> foundCollections;
    private java.util.Map<String, String> foundPlaylists;
    private final Lazy<org.jellyfin.sdk.api.client.ApiClient> api = inject(org.jellyfin.sdk.api.client.ApiClient.class);
    private final Lazy<UserPreferences> userPreferences = inject(UserPreferences.class);
    private final Lazy<DataRefreshService> dataRefreshService = inject(DataRefreshService.class);
    private final Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);
    final Lazy<MediaManager> mediaManager = inject(MediaManager.class);
    private final Lazy<MarkdownRenderer> markdownRenderer = inject(MarkdownRenderer.class);
    private final Lazy<org.jellyfin.androidtv.ui.itemdetail.ThemeSongs> themeSongs = inject(org.jellyfin.androidtv.ui.itemdetail.ThemeSongs.class);
    private final Lazy<CustomMessageRepository> customMessageRepository = inject(CustomMessageRepository.class);
    final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);
    private final Lazy<ItemLauncher> itemLauncher = inject(ItemLauncher.class);
    private final Lazy<KeyProcessor> keyProcessor = inject(KeyProcessor.class);
    final Lazy<PlaybackHelper> playbackHelper = inject(PlaybackHelper.class);
    private final Lazy<ImageHelper> imageHelper = inject(ImageHelper.class);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentFullDetailsBinding binding = FragmentFullDetailsBinding.inflate(getLayoutInflater(), container, false);

        BUTTON_SIZE = Utils.convertDpToPixel(requireContext(), 40);

        mMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        mRowsFragment = new RowsSupportFragment();
        getChildFragmentManager().beginTransaction().replace(R.id.rowsFragment, mRowsFragment).commit();

        mRowsFragment.setOnItemViewClickedListener(new ItemViewClickedListener());
        mRowsFragment.setOnItemViewSelectedListener(new ItemViewSelectedListener());

        mDorPresenter = new MyDetailsOverviewRowPresenter(markdownRenderer.getValue());

        mItemId = Utils.uuidOrNull(getArguments().getString("ItemId"));
        mChannelId = Utils.uuidOrNull(getArguments().getString("ChannelId"));
        String programJson = getArguments().getString("ProgramInfo");
        if (programJson != null) {
            mProgramInfo = Json.Default.decodeFromString(BaseItemDto.Companion.serializer(), programJson);
        }
        String timerJson = getArguments().getString("SeriesTimer");
        if (timerJson != null) {
            mSeriesTimerInfo = Json.Default.decodeFromString(SeriesTimerInfoDto.Companion.serializer(), timerJson);
        }

        CoroutineUtils.readCustomMessagesOnLifecycle(getLifecycle(), customMessageRepository.getValue(), message -> {
            if (message.equals(CustomMessage.ActionComplete.INSTANCE) && mSeriesTimerInfo != null) {
                //update info
                FullDetailsFragmentHelperKt.getLiveTvSeriesTimer(this, mSeriesTimerInfo.getId(), seriesTimerInfoDto -> {
                    mSeriesTimerInfo = seriesTimerInfoDto;
                    mBaseItem = JavaCompat.copyWithOverview(mBaseItem, BaseItemUtils.getSeriesOverview(mSeriesTimerInfo, requireContext()));
                    mDorPresenter.getViewHolder().setSummary(mBaseItem.getOverview());
                    return null;
                });

                mRowsAdapter.clear();
                mRowsAdapter.add(mDetailsOverviewRow);
                //re-retrieve the schedule after giving it a second to rebuild
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                            return;

                        addAdditionalRows(mRowsAdapter);

                    }
                }, 1500);
            }
            return null;
        });

        loadItem(mItemId);

        return binding.getRoot();
    }

    int getResumePreroll() {
        try {
            return Integer.parseInt(KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getResumeSubtractDuration())) * 1000;
        } catch (Exception e) {
            Timber.e(e, "Unable to parse resume preroll");
            return 0;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        ClockBehavior clockBehavior = userPreferences.getValue().get(UserPreferences.Companion.getClockBehavior());
        if (clockBehavior == ClockBehavior.ALWAYS || clockBehavior == ClockBehavior.IN_MENUS) {
            startClock();
        }

        //Update information that may have changed - delay slightly to allow changes to take on the server
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

                Instant lastPlaybackTime = dataRefreshService.getValue().getLastPlayback();
                Timber.d("current time %s last playback event time %s last refresh time %s", Instant.now().toEpochMilli(), lastPlaybackTime, mLastUpdated.toEpochMilli());

                // if last playback event exists, and event time is greater than last sync or within 2 seconds of current time
                // the third condition accounts for a situation where a sync (dataRefresh) coincides with the end of playback
                if (lastPlaybackTime != null && (lastPlaybackTime.isAfter(mLastUpdated) || Instant.now().toEpochMilli() - lastPlaybackTime.toEpochMilli() < 2000) && mBaseItem.getType() != BaseItemKind.MUSIC_ARTIST) {
                    BaseItemDto lastPlayedItem = dataRefreshService.getValue().getLastPlayedItem();
                    if (mBaseItem.getType() == BaseItemKind.EPISODE && lastPlayedItem != null && !mBaseItem.getId().equals(lastPlayedItem.getId().toString()) && lastPlayedItem.getType() == BaseItemKind.EPISODE) {
                        Timber.i("Re-loading after new episode playback");
                        loadItem(lastPlayedItem.getId());
                        dataRefreshService.getValue().setLastPlayedItem(null); //blank this out so a detail screen we back up to doesn't also do this
                    } else {
                        Timber.d("Updating info after playback");
                        FullDetailsFragmentHelperKt.getItem(FullDetailsFragment.this, mBaseItem.getId(), item -> {
                            if (item == null) return null;

                            mBaseItem = item;
                            // Removed: Resume button visibility logic
                            updateWatched();
                            mLastUpdated = Instant.now();
                            return null;
                        });
                    }
                }
            }
        }, 750);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopClock();
        themeSongs.getValue().fadeOutAndStop();
    }

    @Override
    public void onStop() {
        themeSongs.getValue().fadeOutAndStop();
        super.onStop();
        stopClock();
    }

    @Override
    public void onDestroyView() {
        themeSongs.getValue().fadeOutAndStop();
        super.onDestroyView();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;

        if (mCurrentItem != null) {
            return keyProcessor.getValue().handleKey(keyCode, mCurrentItem, requireActivity());
        } else if ((keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) && BaseItemExtensionsKt.canPlay(mBaseItem)) {
            //default play action
            Long pos = mBaseItem.getUserData().getPlaybackPositionTicks() / 10000;
            play(mBaseItem, pos.intValue(), false);
            return true;
        }

        return false;
    }

    private void startClock() {
        mClockLoop = new Runnable() {
            @Override
            public void run() {
                if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
                // View holder may be null when the base item is still loading - this is a rare case
                // which generally happens when the server is unresponsive
                MyDetailsOverviewRowPresenter.ViewHolder viewholder = mDorPresenter.getViewHolder();
                if (viewholder == null) return;

                if (mBaseItem != null && mBaseItem.getRunTimeTicks() != null && mBaseItem.getRunTimeTicks() > 0) {
                    viewholder.setInfoValue3(getEndTime());
                    mLoopHandler.postDelayed(this, 15000);
                }
            }
        };

        mLoopHandler.postDelayed(mClockLoop, 15000);
    }

    private void stopClock() {
        if (mLoopHandler != null && mClockLoop != null) {
            mLoopHandler.removeCallbacks(mClockLoop);
        }
    }

    private static BaseItemKind[] buttonTypes = new BaseItemKind[]{
            BaseItemKind.EPISODE,
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.SEASON,
            BaseItemKind.FOLDER,
            BaseItemKind.VIDEO,
            BaseItemKind.RECORDING,
            BaseItemKind.PROGRAM,
            BaseItemKind.TRAILER,
            BaseItemKind.MUSIC_ARTIST,
            BaseItemKind.PERSON,
            BaseItemKind.MUSIC_VIDEO
    };

    private static List<BaseItemKind> buttonTypeList = Arrays.asList(buttonTypes);

    private void updateWatched() {
        if (mWatchedToggleButton != null && mBaseItem != null && mBaseItem.getUserData() != null) {
            mWatchedToggleButton.setActivated(mBaseItem.getUserData().getPlayed());
        }
    }

    private void loadItem(UUID id) {
        if (mChannelId != null && mProgramInfo == null) {
            // if we are displaying a live tv channel - we want to get whatever is showing now on that channel
            FullDetailsFragmentHelperKt.getLiveTvChannel(this, mChannelId, channel -> {
                mProgramInfo = channel.getCurrentProgram();
                mItemId = mProgramInfo.getId();
                FullDetailsFragmentHelperKt.getItem(FullDetailsFragment.this, mItemId, item -> {
                    if (item != null) {
                        setBaseItem(item);
                    } else {
                        // Failed to load item
                        navigationRepository.getValue().goBack();
                    }
                    return null;
                });
                return null;
            });
        } else if (mSeriesTimerInfo != null) {
            setBaseItem(FullDetailsFragmentHelperKt.createFakeSeriesTimerBaseItemDto(this, mSeriesTimerInfo));
        } else {
            FullDetailsFragmentHelperKt.getItem(FullDetailsFragment.this, id, item -> {
                if (item != null) {
                    setBaseItem(item);
                } else {
                    // Failed to load item
                    navigationRepository.getValue().goBack();
                }
                return null;
            });
        }

        mLastUpdated = Instant.now();
    }

    @Override
    public void setRecTimer(String id) {
        mProgramInfo = JavaCompat.copyWithTimerId(mProgramInfo, id);
        if (mRecordButton != null) mRecordButton.setActivated(id != null);
    }

    private int posterHeight;


    private void preloadVideoVersions() {
        if (mBaseItem == null || mBaseItem.getMediaSources() == null || mBaseItem.getMediaSources().size() <= 1) {
            Timber.d("Skipping version preload - no multiple media sources found");
            return;
        }

        Timber.d("Starting batch probing for %d video versions", mBaseItem.getMediaSources().size());

        List<org.jellyfin.sdk.model.api.MediaSourceInfo> probedSources = new ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        int totalSources = mBaseItem.getMediaSources().size();

        for (org.jellyfin.sdk.model.api.MediaSourceInfo mediaSource : mBaseItem.getMediaSources()) {
            probeMediaSource(mediaSource.getId(), probedSource -> {
                synchronized (probedSources) {
                    if (probedSource != null) {
                        probedSources.add(probedSource);
                    }
                }

                int completed = completedCount.incrementAndGet();
                Timber.d("Completed probing %d/%d video versions", completed, totalSources);

                if (completed == totalSources) {
                    if (!probedSources.isEmpty()) {
                        mBaseItem = JavaCompat.copyWithMediaSources(mBaseItem, probedSources);
                        Timber.d("Successfully probed %d video versions with complete stream information", probedSources.size());

                        if (versions != null) {
                            versions = new ArrayList<>(probedSources);
                        }
                    } else {
                        Timber.w("No media sources were successfully probed");
                    }
                }
            });
        }
    }
    private void probeMediaSource(String mediaSourceId, java.util.function.Consumer<org.jellyfin.sdk.model.api.MediaSourceInfo> callback) {
        try {
            UserPreferences userPreferences = KoinJavaComponent.get(UserPreferences.class);

            // Create device profile for probing
            DeviceProfile deviceProfile = org.jellyfin.androidtv.util.profile.DeviceProfileKt.createDeviceProfile(userPreferences, false);

            FullDetailsFragmentHelperKt.getPostedPlaybackInfo(this, mBaseItem.getId(), mediaSourceId, deviceProfile, response -> {
                if (response != null) {
                    if (response.getErrorCode() != null) {
                        Timber.w("Playback info error for source %s: %s", mediaSourceId, response.getErrorCode());
                        callback.accept(null);
                        return null;
                    }

                    org.jellyfin.sdk.model.api.MediaSourceInfo probedSource = response.getMediaSources().stream()
                        .filter(source -> mediaSourceId.equals(source.getId()))
                        .findFirst()
                        .orElse(null);

                    if (probedSource != null) {
                        Timber.d("Successfully probed media source: %s", mediaSourceId);
                        callback.accept(probedSource);
                        return null;
                    } else {
                        Timber.w("Media source not found in response: %s", mediaSourceId);
                        callback.accept(null);
                        return null;
                    }
                } else {
                    callback.accept(null);
                    return null;
                }
            });

        } catch (Exception e) {
            Timber.e(e, "Exception probing media source: %s", mediaSourceId);
            callback.accept(null);
        }
    }

    @Override
    public void setRecSeriesTimer(String id) {
        if (mProgramInfo != null) mProgramInfo = JavaCompat.copyWithTimerId(mProgramInfo, id);
        if (mRecSeriesButton != null) mRecSeriesButton.setActivated(id != null);
        if (mSeriesSettingsButton != null)
            mSeriesSettingsButton.setVisibility(id == null ? View.GONE : View.VISIBLE);

    }

    private class BuildDorTask extends AsyncTask<BaseItemDto, Integer, MyDetailsOverviewRow> {

        @Override
        protected MyDetailsOverviewRow doInBackground(BaseItemDto... params) {
            BaseItemDto item = params[0];

            // Figure image size
            Double aspect = imageHelper.getValue().getImageAspectRatio(item, false);
            posterHeight = aspect > 1 ? Utils.convertDpToPixel(requireContext(), 160) : Utils.convertDpToPixel(requireContext(), item.getType() == BaseItemKind.PERSON || item.getType() == BaseItemKind.MUSIC_ARTIST ? 300 : 200);

            mDetailsOverviewRow = new MyDetailsOverviewRow(item);

            String primaryImageUrl = imageHelper.getValue().getLogoImageUrl(mBaseItem, 600);
            if (primaryImageUrl == null) {
                primaryImageUrl = imageHelper.getValue().getPrimaryImageUrl(mBaseItem, false, null, posterHeight);
            }

            mDetailsOverviewRow.setSummary(item.getOverview());
            switch (item.getType()) {
                case PERSON:
                case MUSIC_ARTIST:
                    break;
                default:

                    BaseItemPerson director = BaseItemExtensionsKt.getFirstPerson(item, PersonKind.DIRECTOR);

                    InfoItem firstRow;
                    if (item.getType() == BaseItemKind.SERIES) {
                        firstRow = new InfoItem(
                                getString(R.string.lbl_seasons),
                                String.format("%d", Utils.getSafeValue(item.getChildCount(), 0)));
                    } else {
                        firstRow = new InfoItem(
                                getString(R.string.lbl_directed_by),
                                director != null ? director.getName() : getString(R.string.lbl_bracket_unknown));
                    }
                    mDetailsOverviewRow.setInfoItem1(firstRow);

                    if (item.getRunTimeTicks() != null && item.getRunTimeTicks() > 0) {
                        mDetailsOverviewRow.setInfoItem2(new InfoItem(getString(R.string.lbl_runs), getRunTime()));
                        ClockBehavior clockBehavior = userPreferences.getValue().get(UserPreferences.Companion.getClockBehavior());
                        if (clockBehavior == ClockBehavior.ALWAYS || clockBehavior == ClockBehavior.IN_MENUS) {
                            mDetailsOverviewRow.setInfoItem3(new InfoItem(getString(R.string.lbl_ends), getEndTime()));
                        } else {
                            mDetailsOverviewRow.setInfoItem3(new InfoItem());
                        }
                    } else {
                        mDetailsOverviewRow.setInfoItem2(new InfoItem());
                        mDetailsOverviewRow.setInfoItem3(new InfoItem());
                    }

            }

            mDetailsOverviewRow.setImageDrawable(primaryImageUrl);

            return mDetailsOverviewRow;
        }

        @Override
        protected void onPostExecute(MyDetailsOverviewRow detailsOverviewRow) {
            super.onPostExecute(detailsOverviewRow);

            if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

            ClassPresenterSelector ps = new ClassPresenterSelector();
            ps.addClassPresenter(MyDetailsOverviewRow.class, mDorPresenter);
            mListRowPresenter = new CustomListRowPresenter(Utils.convertDpToPixel(requireContext(), -12), Utils.convertDpToPixel(requireContext(), -25));
            ps.addClassPresenter(ListRow.class, mListRowPresenter);
            mRowsAdapter = new MutableObjectAdapter<Row>(ps);
            mRowsFragment.setAdapter(mRowsAdapter);
            mRowsAdapter.add(detailsOverviewRow);

            updateInfo(detailsOverviewRow.getItem());
            addAdditionalRows(mRowsAdapter);

        }
    }

    public void setBaseItem(BaseItemDto item) {
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

        mBaseItem = item;
        // Fetch image quality preference
        UserPreferences userPreferences = KoinJavaComponent.get(UserPreferences.class);
        String imageQuality = userPreferences.get(UserPreferences.Companion.getImageQuality());
        int bgWidth = 1280; // default for 'normal'
        int bgHeight = 720;
        if ("low".equals(imageQuality)) {
            bgWidth = 640;
            bgHeight = 360;
        } else if ("high".equals(imageQuality)) {
            bgWidth = 1920;
            bgHeight = 1080;
        }
        if (item.getType() != BaseItemKind.PERSON && item.getType() != BaseItemKind.MUSIC_ARTIST) {
            backgroundService.getValue().setBackground(item);
        } else {
            backgroundService.getValue().clearBackgrounds();
        }
        if (mBaseItem != null) {
            if (mChannelId != null) {
                mBaseItem = JavaCompat.copyWithParentId(mBaseItem, mChannelId);
                mBaseItem = JavaCompat.copyWithDates(
                        mBaseItem,
                        mProgramInfo.getStartDate(),
                        mProgramInfo.getEndDate(),
                        mBaseItem.getOfficialRating(),
                        mProgramInfo.getRunTimeTicks()
                );
            }
            preloadVideoVersions();
            new BuildDorTask().execute(item);
            themeSongs.getValue().playThemeSong(mBaseItem, true);
        }
    }

    protected void addItemRow(MutableObjectAdapter<Row> parent, ItemRowAdapter row, int index, String headerText) {
        HeaderItem header = new HeaderItem(index, headerText);
        ListRow listRow = new ListRow(header, row);
        parent.add(listRow);
        row.setRow(listRow);
        row.Retrieve();
    }

    protected void addAdditionalRows(MutableObjectAdapter<Row> adapter) {
        Timber.d("Item type: %s", mBaseItem.getType().toString());

        if (mSeriesTimerInfo != null) {
            TvManager.getScheduleRowsAsync(this, mSeriesTimerInfo.getId(), new CardPresenter(true), adapter);
            return;
        }

        switch (mBaseItem.getType()) {
            case MOVIE:

                //Additional Parts
                if (mBaseItem.getPartCount() != null && mBaseItem.getPartCount() > 0) {
                    ItemRowAdapter additionalPartsAdapter = new ItemRowAdapter(requireContext(), new GetAdditionalPartsRequest(mBaseItem.getId()), new CardPresenter(), adapter);
                    addItemRow(adapter, additionalPartsAdapter, 0, getString(R.string.lbl_additional_parts));
                }

                //Cast/Crew
                if (mBaseItem.getPeople() != null && !mBaseItem.getPeople().isEmpty()) {
                    ItemRowAdapter castAdapter = new ItemRowAdapter(mBaseItem.getPeople(), requireContext(), new CardPresenter(true, 130), adapter);
                    addItemRow(adapter, castAdapter, 1, getString(R.string.lbl_cast_crew));
                }

                //Specials
                if (mBaseItem.getSpecialFeatureCount() != null && mBaseItem.getSpecialFeatureCount() > 0) {
                    addItemRow(adapter, new ItemRowAdapter(requireContext(), new GetSpecialsRequest(mBaseItem.getId()), new CardPresenter(), adapter), 3, getString(R.string.lbl_specials));
                }

                //Trailers
                if (mBaseItem.getLocalTrailerCount() != null && mBaseItem.getLocalTrailerCount() > 1) {
                    addItemRow(adapter, new ItemRowAdapter(requireContext(), new GetTrailersRequest(mBaseItem.getId()), new CardPresenter(), adapter), 4, getString(R.string.lbl_trailers));
                }

                //Chapters
                if (mBaseItem.getChapters() != null && !mBaseItem.getChapters().isEmpty()) {
                    List<ChapterItemInfo> chapters = BaseItemExtensionsKt.buildChapterItems(mBaseItem, api.getValue());
                    ItemRowAdapter chapterAdapter = new ItemRowAdapter(requireContext(), chapters, new CardPresenter(true, 120), adapter);
                    addItemRow(adapter, chapterAdapter, 2, getString(R.string.lbl_chapters));
                }

                //Similar
                ItemRowAdapter similarMoviesAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSimilarItemsRequest(mBaseItem.getId()), QueryType.SimilarMovies, new CardPresenter(), adapter);
                addItemRow(adapter, similarMoviesAdapter, 5, getString(R.string.lbl_more_like_this));

                addInfoRows(adapter);
                break;
            case TRAILER:

                //Cast/Crew
                if (mBaseItem.getPeople() != null && !mBaseItem.getPeople().isEmpty()) {
                    ItemRowAdapter castAdapter = new ItemRowAdapter(mBaseItem.getPeople(), requireContext(), new CardPresenter(true, 130), adapter);
                    addItemRow(adapter, castAdapter, 0, getString(R.string.lbl_cast_crew));
                }

                //Similar
                ItemRowAdapter similarTrailerAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSimilarItemsRequest(mBaseItem.getId()), QueryType.SimilarMovies, new CardPresenter(), adapter);
                addItemRow(adapter, similarTrailerAdapter, 4, getString(R.string.lbl_more_like_this));
                addInfoRows(adapter);
                break;
            case PERSON:
                ItemRowAdapter personMoviesAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createPersonItemsRequest(mBaseItem.getId(), BaseItemKind.MOVIE), 100, false, new CardPresenter(), adapter);
                addItemRow(adapter, personMoviesAdapter, 0, getString(R.string.lbl_movies));

                ItemRowAdapter personSeriesAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createPersonItemsRequest(mBaseItem.getId(), BaseItemKind.SERIES), 100, false, new CardPresenter(), adapter);
                addItemRow(adapter, personSeriesAdapter, 1, getString(R.string.lbl_tv_series));

                ItemRowAdapter personEpisodesAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createPersonItemsRequest(mBaseItem.getId(), BaseItemKind.EPISODE), 100, false, new CardPresenter(), adapter);
                addItemRow(adapter, personEpisodesAdapter, 2, getString(R.string.lbl_episodes));

                break;
            case MUSIC_ARTIST:
                ItemRowAdapter artistAlbumsAdapter = new ItemRowAdapter(requireContext(),  BrowsingUtils.createArtistItemsRequest(mBaseItem.getId(), BaseItemKind.MUSIC_ALBUM), 100, false, new CardPresenter(), adapter);
                addItemRow(adapter, artistAlbumsAdapter, 0, getString(R.string.lbl_albums));

                break;
            case SERIES:
                // Removed: Next Up section
                // ItemRowAdapter nextUpAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSeriesGetNextUpRequest(mBaseItem.getId()), false, new CardPresenter(true, 130), adapter);
                // addItemRow(adapter, nextUpAdapter, 0, getString(R.string.lbl_next_up));

                ItemRowAdapter seasonsAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSeasonsRequest(mBaseItem.getId()), new CardPresenter(), adapter);
                addItemRow(adapter, seasonsAdapter, 0, getString(R.string.lbl_seasons));

                ItemRowAdapter upcomingAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createUpcomingEpisodesRequest(mBaseItem.getId()), new CardPresenter(), adapter);
                addItemRow(adapter, upcomingAdapter, 1, getString(R.string.lbl_upcoming));

                //Specials
                if (mBaseItem.getSpecialFeatureCount() != null && mBaseItem.getSpecialFeatureCount() > 0) {
                    addItemRow(adapter, new ItemRowAdapter(requireContext(), new GetSpecialsRequest(mBaseItem.getId()), new CardPresenter(), adapter), 2, getString(R.string.lbl_specials));
                }

                if (mBaseItem.getPeople() != null && !mBaseItem.getPeople().isEmpty()) {
                    ItemRowAdapter seriesCastAdapter = new ItemRowAdapter(mBaseItem.getPeople(), requireContext(), new CardPresenter(true, 130), adapter);
                    addItemRow(adapter, seriesCastAdapter, 3, getString(R.string.lbl_cast_crew));
                }

                ItemRowAdapter similarAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSimilarItemsRequest(mBaseItem.getId()), QueryType.SimilarSeries, new CardPresenter(), adapter);
                addItemRow(adapter, similarAdapter, 4, getString(R.string.lbl_more_like_this));
                break;

            case EPISODE:
                if (mBaseItem.getSeasonId() != null && mBaseItem.getIndexNumber() != null) {
                    // query index is zero-based but episode no is not
                    ItemRowAdapter nextAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createNextEpisodesRequest(mBaseItem.getSeasonId(), mBaseItem.getIndexNumber()), 0, false, true, new CardPresenter(true, 120), adapter);
                    addItemRow(adapter, nextAdapter, 5, getString(R.string.lbl_next_episode));
                }

                //Guest stars
                if (mBaseItem.getPeople() != null && !mBaseItem.getPeople().isEmpty()) {
                    List<BaseItemPerson> guests = new ArrayList<>();
                    for (BaseItemPerson person : mBaseItem.getPeople()) {
                        if (person.getType() == PersonKind.GUEST_STAR) guests.add(person);
                    }
                    if (!guests.isEmpty()) {
                        ItemRowAdapter castAdapter = new ItemRowAdapter(guests, requireContext(), new CardPresenter(true, 130), adapter);
                        addItemRow(adapter, castAdapter, 0, getString(R.string.lbl_guest_stars));
                    }
                }

                //Chapters
                if (mBaseItem.getChapters() != null && !mBaseItem.getChapters().isEmpty()) {
                    List<ChapterItemInfo> chapters = BaseItemExtensionsKt.buildChapterItems(mBaseItem, api.getValue());
                    ItemRowAdapter chapterAdapter = new ItemRowAdapter(requireContext(), chapters, new CardPresenter(true, 120), adapter);
                    addItemRow(adapter, chapterAdapter, 1, getString(R.string.lbl_chapters));
                }

                addInfoRows(adapter);
                break;

            default:
                addInfoRows(adapter);
        }
    }

    private void addInfoRows(MutableObjectAdapter<Row> adapter) {
        if (KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getDebuggingEnabled()) && mBaseItem.getMediaSources() != null) {
            for (MediaSourceInfo ms : mBaseItem.getMediaSources()) {
                if (ms.getMediaStreams() != null && !ms.getMediaStreams().isEmpty()) {
                    HeaderItem header = new HeaderItem("Media Details" + (ms.getContainer() != null ? " (" + ms.getContainer() + ")" : ""));
                    ArrayObjectAdapter infoAdapter = new ArrayObjectAdapter(new InfoCardPresenter());
                    for (MediaStream stream : ms.getMediaStreams()) {
                        infoAdapter.add(stream);
                    }

                    adapter.add(new ListRow(header, infoAdapter));

                }
            }
        }
    }

    private void updateInfo(BaseItemDto item) {
        if (buttonTypeList.contains(item.getType())) {
            mDetailsOverviewRow.clearActions();
            addButtons(BUTTON_SIZE);
        }

        mLastUpdated = Instant.now();
    }

    public void setTitle(String title) {
        mDorPresenter.getViewHolder().setTitle(title);
    }

    private String getRunTime() {
        Long runtime = Utils.getSafeValue(mBaseItem.getRunTimeTicks(), mBaseItem.getRunTimeTicks());

        if (runtime == null || runtime <= 0) {
            return "";
        }

        int totalMinutes = (int) Math.ceil((double) runtime / 600000000);

        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;

        if (hours > 0) {
            return getString(R.string.runtime_hours_minutes, hours, minutes);
        }

        return getString(R.string.runtime_minutes, minutes);
    }

    private String getEndTime() {
        if (mBaseItem != null && mBaseItem.getType() != BaseItemKind.MUSIC_ARTIST && mBaseItem.getType() != BaseItemKind.PERSON) {
            Long runtime = Utils.getSafeValue(mBaseItem.getRunTimeTicks(), mBaseItem.getRunTimeTicks());
            if (runtime != null && runtime > 0) {
                LocalDateTime endTime = mBaseItem.getType() == BaseItemKind.PROGRAM && mBaseItem.getEndDate() != null ? mBaseItem.getEndDate() : LocalDateTime.now().plusNanos(runtime * 100);
                if (JavaCompat.getCanResume(mBaseItem)) {
                    endTime = LocalDateTime.now().plusNanos((runtime - mBaseItem.getUserData().getPlaybackPositionTicks()) * 100);
                }
                return DateTimeExtensionsKt.getTimeFormatter(getContext()).format(endTime);
            }

        }
        return "";
    }

    void addItemToQueue() {
        BaseItemDto baseItem = mBaseItem;
        if (baseItem.getType() == BaseItemKind.AUDIO || baseItem.getType() == BaseItemKind.MUSIC_ALBUM || baseItem.getType() == BaseItemKind.MUSIC_ARTIST) {
            if (baseItem.getType() == BaseItemKind.MUSIC_ALBUM || baseItem.getType() == BaseItemKind.MUSIC_ARTIST) {
                playbackHelper.getValue().getItemsToPlay(getContext(), baseItem, false, false, new Response<List<BaseItemDto>>() {
                    @Override
                    public void onResponse(List<BaseItemDto> response) {
                        mediaManager.getValue().addToAudioQueue(response);
                    }
                });
            } else {
                mediaManager.getValue().queueAudioItem(baseItem);
            }
        }
    }

    void gotoSeries() {
        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(mBaseItem.getSeriesId()));
    }

    private void deleteItem() {
        Timber.i("Showing item delete confirmation");
        new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setTitle(getString(R.string.item_delete_confirm_title))
                .setMessage(getString(R.string.item_delete_confirm_message))
                .setNegativeButton(R.string.lbl_no, null)
                .setPositiveButton(R.string.lbl_delete, (dialog, which) -> {
                    FullDetailsFragmentHelperKt.deleteItem(
                            this,
                            api.getValue(),
                            mBaseItem,
                            dataRefreshService.getValue(),
                            navigationRepository.getValue()
                    );
                })
                .show();
    }

    DetailButton favButton = null;
    DetailButton shuffleButton = null;
    DetailButton goToSeriesButton = null;
    DetailButton queueButton = null;
    DetailButton deleteButton = null;
    DetailButton moreButton;
    DetailButton playButton = null;
    DetailButton trailerButton = null;
    DetailButton setAnimeLibraryButton = null;

    // Play button state for loading animation
    private int playButtonOriginalIcon = R.drawable.ic_play;
    private String playButtonOriginalText = "";

    private void addButtons(int buttonSize) {
        BaseItemDto baseItem = mBaseItem;
        String buttonLabel;
        if (baseItem.getType() == BaseItemKind.SERIES) {
            buttonLabel = getString(R.string.lbl_play_next_up);
        } else {
            long startPos = 0;
            if (JavaCompat.getCanResume(mBaseItem)) {
                startPos = (mBaseItem.getUserData().getPlaybackPositionTicks() / 10000) - getResumePreroll();
            }
            buttonLabel = getString(R.string.lbl_resume_from, TimeUtils.formatMillis(startPos));
        }
        mResumeButton = DetailButton.create(requireContext(), R.drawable.ic_resume, buttonLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FullDetailsFragmentHelperKt.resumePlayback(FullDetailsFragment.this);
            }
        });

        if (JavaCompat.getCanResume(mBaseItem) && mBaseItem.getRunTimeTicks() != null && mBaseItem.getRunTimeTicks() > 0) {
            float progressPercentage = (float) (mBaseItem.getUserData().getPlaybackPositionTicks() * 100.0 / mBaseItem.getRunTimeTicks());
            mResumeButton.setProgress(progressPercentage / 100f);
        }

        if (BaseItemExtensionsKt.canPlay(baseItem) && !Utils.getSafeValue(mBaseItem.isFolder(), false)) {
            // Removed: mDetailsOverviewRow.addAction(mResumeButton);
            // Removed: boolean resumeButtonVisible = (baseItem.getType() == BaseItemKind.SERIES && !mBaseItem.getUserData().getPlayed()) || (JavaCompat.getCanResume(mBaseItem));
            // Removed: mResumeButton.setVisibility(resumeButtonVisible ? View.VISIBLE : View.GONE);

            String playButtonText;
            if (BaseItemExtensionsKt.isLiveTv(mBaseItem)) {
                playButtonText = getString(R.string.lbl_tune_to_channel);
            } else if (mBaseItem.getType() == BaseItemKind.MOVIE) {
                playButtonText = getString(R.string.lbl_play) + " " + (mBaseItem.getName() != null ? mBaseItem.getName() : getString(R.string.lbl_bracket_unknown));
            } else if (mBaseItem.getType() == BaseItemKind.EPISODE) {
                String seriesName = mBaseItem.getSeriesName() != null ? mBaseItem.getSeriesName() : getString(R.string.lbl_bracket_unknown);
                String seasonNumber = mBaseItem.getParentIndexNumber() != null ? mBaseItem.getParentIndexNumber().toString() : "?";
                String episodeNumber = mBaseItem.getIndexNumber() != null ? mBaseItem.getIndexNumber().toString() : "?";
                playButtonText = getString(R.string.lbl_play) + " " + seriesName + " season " + seasonNumber + " episode " + episodeNumber;
            } else {
                playButtonText = getString(R.string.lbl_play);
            }
            playButtonOriginalIcon = R.drawable.ic_play;
            playButtonOriginalText = playButtonText;

            playButton = DetailButton.create(requireContext(), playButtonOriginalIcon, playButtonOriginalText, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Check if any scraper is enabled
                    boolean torrentioEnabled = userPreferences.getValue().get(UserPreferences.Companion.getTorrentioEnabled());
                    boolean aiostreamsEnabled = userPreferences.getValue().get(UserPreferences.Companion.getAiostreamsEnabled());

                    if (torrentioEnabled || aiostreamsEnabled) {
                        // Show loading state and query scrapers
                        setPlayButtonLoadingState();
                        queryAndShowStreams();
                    } else {
                        // Use original play behavior
                        play(mBaseItem, 0, false);
                    }
                }
            });

            mDetailsOverviewRow.addAction(playButton);

            // Removed: Resume button visibility check (no longer needed)

            boolean isMusic = baseItem.getType() == BaseItemKind.MUSIC_ALBUM
                    || baseItem.getType() == BaseItemKind.MUSIC_ARTIST
                    || baseItem.getType() == BaseItemKind.AUDIO
                    || (baseItem.getType() == BaseItemKind.PLAYLIST && MediaType.AUDIO.equals(baseItem.getMediaType()));

            if (isMusic) {
                queueButton = DetailButton.create(requireContext(), R.drawable.ic_add, getString(R.string.lbl_add_to_queue), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addItemToQueue();
                    }
                });
                mDetailsOverviewRow.addAction(queueButton);
            }

            // Removed: Shuffle all button
            // if (Utils.getSafeValue(mBaseItem.isFolder(), false) || baseItem.getType() == BaseItemKind.MUSIC_ARTIST) {
            //     shuffleButton = DetailButton.create(requireContext(), R.drawable.ic_shuffle, getString(R.string.lbl_shuffle_all), new View.OnClickListener() {
            //         @Override
            //         public void onClick(View v) {
            //             play(mBaseItem, 0, true);
            //         }
            //     });
            //     mDetailsOverviewRow.addAction(shuffleButton);
            // }

            if (baseItem.getType() == BaseItemKind.MUSIC_ARTIST) {
                DetailButton imix = DetailButton.create(requireContext(), R.drawable.ic_mix, getString(R.string.lbl_instant_mix), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        playbackHelper.getValue().playInstantMix(requireContext(), baseItem);
                    }
                });
                mDetailsOverviewRow.addAction(imix);
            }
        }
        //Video versions button
        if (mBaseItem.getMediaSources() != null && mBaseItem.getMediaSources().size() > 1) {
            mVersionsButton = DetailButton.create(requireContext(), R.drawable.ic_guide, getString(R.string.select_version), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (versions != null) {
                        addVersionsMenu(v);
                    } else {
                        versions = new ArrayList<>(mBaseItem.getMediaSources());
                        addVersionsMenu(v);
                    }
                }
            });
            mDetailsOverviewRow.addAction(mVersionsButton);
        }

        if (TrailerUtils.hasPlayableTrailers(requireContext(), mBaseItem)) {
            trailerButton = DetailButton.create(requireContext(), R.drawable.ic_trailer, getString(R.string.lbl_play_trailers), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FullDetailsFragmentHelperKt.playTrailers(FullDetailsFragment.this);
                }
            });

            mDetailsOverviewRow.addAction(trailerButton);
        }

        if (mProgramInfo != null && Utils.canManageRecordings(KoinJavaComponent.<UserRepository>get(UserRepository.class).getCurrentUser().getValue())) {
            if (mBaseItem.getEndDate().isAfter(LocalDateTime.now())) {
                //Record button
                mRecordButton = DetailButton.create(requireContext(), R.drawable.ic_record, getString(R.string.lbl_record), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mProgramInfo.getTimerId() == null) {
                            //Create one-off recording with defaults
                            FullDetailsFragmentHelperKt.getLiveTvDefaultTimer(FullDetailsFragment.this, mProgramInfo.getId(), seriesTimer -> {
                                FullDetailsFragmentHelperKt.createLiveTvSeriesTimer(FullDetailsFragment.this, seriesTimer, () -> {
                                    FullDetailsFragmentHelperKt.getLiveTvProgram(FullDetailsFragment.this, mProgramInfo.getId(), program -> {
                                        mProgramInfo = program;
                                        setRecSeriesTimer(program.getSeriesTimerId());
                                        setRecTimer(program.getTimerId());
                                        Utils.showToast(requireContext(), R.string.msg_set_to_record);
                                        return null;
                                    });
                                    return null;
                                });
                                return null;
                            });
                        } else {
                            FullDetailsFragmentHelperKt.cancelLiveTvSeriesTimer(FullDetailsFragment.this, mProgramInfo.getTimerId(), () -> {
                                setRecTimer(null);
                                dataRefreshService.getValue().setLastDeletedItemId(mProgramInfo.getId());
                                Utils.showToast(requireContext(), R.string.msg_recording_cancelled);
                                return null;
                            });
                        }
                    }
                });
                mRecordButton.setActivated(mProgramInfo.getTimerId() != null);

                mDetailsOverviewRow.addAction(mRecordButton);
            }

            if (mProgramInfo.isSeries() != null && mProgramInfo.isSeries()) {
                mRecSeriesButton = DetailButton.create(requireContext(), R.drawable.ic_record_series, getString(R.string.lbl_record_series), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mProgramInfo.getSeriesTimerId() == null) {
                            //Create series recording with default options
                            FullDetailsFragmentHelperKt.getLiveTvDefaultTimer(FullDetailsFragment.this, mProgramInfo.getId(), seriesTimer -> {
                                FullDetailsFragmentHelperKt.createLiveTvSeriesTimer(FullDetailsFragment.this, seriesTimer, () -> {
                                    FullDetailsFragmentHelperKt.getLiveTvProgram(FullDetailsFragment.this, mProgramInfo.getId(), program -> {
                                        mProgramInfo = program;
                                        setRecSeriesTimer(program.getSeriesTimerId());
                                        setRecTimer(program.getTimerId());
                                        Utils.showToast(requireContext(), R.string.msg_set_to_record);
                                        return null;
                                    });
                                    return null;
                                });
                                return null;
                            });
                        } else {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(getString(R.string.lbl_cancel_series))
                                    .setMessage(getString(R.string.msg_cancel_entire_series))
                                    .setNegativeButton(R.string.lbl_no, null)
                                    .setPositiveButton(R.string.lbl_yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            FullDetailsFragmentHelperKt.cancelLiveTvSeriesTimer(FullDetailsFragment.this, mProgramInfo.getSeriesTimerId(), () -> {
                                                setRecSeriesTimer(null);
                                                setRecTimer(null);
                                                dataRefreshService.getValue().setLastDeletedItemId(mProgramInfo.getId());
                                                Utils.showToast(requireContext(), R.string.msg_recording_cancelled);
                                                return null;
                                            });
                                        }
                                    }).show();
                        }
                    }
                });
                mRecSeriesButton.setActivated(mProgramInfo.getSeriesTimerId() != null);

                mDetailsOverviewRow.addAction(mRecSeriesButton);

                mSeriesSettingsButton = DetailButton.create(requireContext(), R.drawable.ic_settings, getString(R.string.lbl_series_settings), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showRecordingOptions(mProgramInfo.getSeriesTimerId(), mProgramInfo, true);
                    }
                });

                mSeriesSettingsButton.setVisibility(mProgramInfo.getSeriesTimerId() != null ? View.VISIBLE : View.GONE);

                mDetailsOverviewRow.addAction(mSeriesSettingsButton);
            }
        }

        org.jellyfin.sdk.model.api.UserItemDataDto userData = mBaseItem.getUserData();
        if (userData != null && mProgramInfo == null) {
            if (mBaseItem.getType() != BaseItemKind.MUSIC_ARTIST && mBaseItem.getType() != BaseItemKind.PERSON && mBaseItem.getType() != BaseItemKind.SERIES) {
                mWatchedToggleButton = DetailButton.create(requireContext(), R.drawable.ic_watch, userData.getPlayed() ? getString(R.string.mark_unwatched) : getString(R.string.mark_watched), markWatchedListener);
                mWatchedToggleButton.setActivated(userData.getPlayed());
                mDetailsOverviewRow.addAction(mWatchedToggleButton);
                mCollectionsButton = DetailButton.create(requireContext(), R.drawable.ic_folder, getString(R.string.lbl_collections), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showCollectionsDialog();
                    }
                });
                mDetailsOverviewRow.addAction(mCollectionsButton);

                mPlaylistsButton = DetailButton.create(requireContext(), R.drawable.ic_add, getString(R.string.lbl_playlists), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPlaylistActionDialog();
                    }
                });
                mDetailsOverviewRow.addAction(mPlaylistsButton);

                // Add "Set Anime Library" button if enabled in settings
                boolean showAnimeLibraryButton = userPreferences.getValue().get(UserPreferences.Companion.getShowSetAnimeLibraryButton());
                if (showAnimeLibraryButton) {
                    setAnimeLibraryButton = DetailButton.create(requireContext(), R.drawable.ic_folder, getString(R.string.lbl_set_anime_library), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            setAnimeLibraryFromCurrentItem();
                        }
                    });
                    mDetailsOverviewRow.addAction(setAnimeLibraryButton);
                }
            }
        }

        mPrevButton = DetailButton.create(requireContext(), R.drawable.arrow_back, getString(R.string.lbl_previous_episode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPrevItemId != null) {
                    navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(mPrevItemId));
                }
            }
        });
        mDetailsOverviewRow.addAction(mPrevButton);
        mPrevButton.setVisibility(View.GONE);

        goToSeriesButton = DetailButton.create(requireContext(), R.drawable.go_back, getString(R.string.lbl_goto_series), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoSeries();
            }
        });
        mDetailsOverviewRow.addAction(goToSeriesButton);
        goToSeriesButton.setVisibility(View.GONE);

        if (mBaseItem.getType() == BaseItemKind.EPISODE && mBaseItem.getSeriesId() != null) {
            FullDetailsFragmentHelperKt.populatePreviousButton(FullDetailsFragment.this);
        }

        if (userPreferences.getValue().get(UserPreferences.Companion.getMediaManagementEnabled())) {
            boolean deletableItem = false;
            UserDto currentUser = KoinJavaComponent.<UserRepository>get(UserRepository.class).getCurrentUser().getValue();
            if (mBaseItem.getType() == BaseItemKind.RECORDING && currentUser.getPolicy().getEnableLiveTvManagement() && mBaseItem.getCanDelete() != null)
                deletableItem = mBaseItem.getCanDelete();
            else if (mBaseItem.getCanDelete() != null) deletableItem = mBaseItem.getCanDelete();

            if (deletableItem) {
                deleteButton = DetailButton.create(requireContext(), R.drawable.ic_delete, getString(R.string.lbl_delete), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        deleteItem();
                    }
                });
                mDetailsOverviewRow.addAction(deleteButton);
            }
        }

        if (mSeriesTimerInfo != null) {
            //Settings
            mDetailsOverviewRow.addAction(DetailButton.create(requireContext(), R.drawable.ic_settings, getString(R.string.lbl_series_settings), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //show recording options
                    showRecordingOptions(mSeriesTimerInfo.getId(), mBaseItem, true);
                }
            }));

            //Delete
            DetailButton del = DetailButton.create(requireContext(), R.drawable.ic_trash, getString(R.string.lbl_cancel_series), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.lbl_delete)
                            .setMessage(getString(R.string.msg_cancel_entire_series))
                            .setPositiveButton(R.string.lbl_cancel_series, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    FullDetailsFragmentHelperKt.cancelLiveTvSeriesTimer(FullDetailsFragment.this, mSeriesTimerInfo.getId(), () -> {
                                        Utils.showToast(requireContext(), getString(R.string.msg_recording_cancelled));
                                        dataRefreshService.getValue().setLastDeletedItemId(UUIDSerializerKt.toUUID(mSeriesTimerInfo.getId()));
                                        if (navigationRepository.getValue().getCanGoBack()) {
                                            navigationRepository.getValue().goBack();
                                        } else {
                                            navigationRepository.getValue().reset(Destinations.INSTANCE.getHome());
                                        }
                                        return null;
                                    });
                                }
                            })
                            .setNegativeButton(R.string.lbl_no, null)
                            .show();

                }
            });
            mDetailsOverviewRow.addAction(del);

        }

        //Now, create a more button to show if needed
        moreButton = DetailButton.create(requireContext(), R.drawable.ic_more, getString(R.string.lbl_other_options), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FullDetailsFragmentHelperKt.showDetailsMenu(FullDetailsFragment.this, v, mBaseItem);
            }
        });

        moreButton.setVisibility(View.GONE);
        mDetailsOverviewRow.addAction(moreButton);
        if (mBaseItem.getType() != BaseItemKind.EPISODE)
            showMoreButtonIfNeeded();  //Episodes check for previous and then call this above
    }

    private void addVersionsMenu(View v) {
        PopupMenu menu = new PopupMenu(requireContext(), v, Gravity.END);

        for (int i = 0; i < versions.size(); i++) {
            menu.getMenu().add(Menu.NONE, i, Menu.NONE, versions.get(i).getName());
        }

        menu.getMenu().setGroupCheckable(0, true, true);
        menu.getMenu().getItem(mDetailsOverviewRow.getSelectedMediaSourceIndex()).setChecked(true);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                mDetailsOverviewRow.setSelectedMediaSourceIndex(menuItem.getItemId());
                FullDetailsFragmentHelperKt.getItem(FullDetailsFragment.this, UUIDSerializerKt.toUUID(versions.get(mDetailsOverviewRow.getSelectedMediaSourceIndex()).getId()), item -> {
                    if (item == null) return null;

                    mBaseItem = item;
                    MyDetailsOverviewRowPresenter.ViewHolder viewholder = mDorPresenter.getViewHolder();
                    if (viewholder != null) {
                        viewholder.setItem(mDetailsOverviewRow);
                    }
                    return null;
                });
                return true;
            }
        });

        menu.show();
    }

    int collapsedOptions = 0;

    void showMoreButtonIfNeeded() {
        int visibleOptions = mDetailsOverviewRow.getVisibleActions();

        List<FrameLayout> actionsList = new ArrayList<>();
        // added in order of priority (should match res/menu/menu_details_more.xml)
        // Include all buttons that can be added to the details row
        if (queueButton != null) actionsList.add(queueButton);
        if (trailerButton != null) actionsList.add(trailerButton);
        if (shuffleButton != null) actionsList.add(shuffleButton);
        if (favButton != null) actionsList.add(favButton);
        // Removed: goToSeriesButton and mPrevButton references (buttons no longer created)
        // if (goToSeriesButton != null) actionsList.add(goToSeriesButton);
        if (mVersionsButton != null) actionsList.add(mVersionsButton);
        if (mRecordButton != null) actionsList.add(mRecordButton);
        if (mRecSeriesButton != null) actionsList.add(mRecSeriesButton);
        if (mSeriesSettingsButton != null) actionsList.add(mSeriesSettingsButton);
        if (mWatchedToggleButton != null) actionsList.add(mWatchedToggleButton);
        if (mCollectionsButton != null) actionsList.add(mCollectionsButton);
        if (mPlaylistsButton != null) actionsList.add(mPlaylistsButton);
        if (setAnimeLibraryButton != null) actionsList.add(setAnimeLibraryButton);
        // Removed: mPrevButton reference (button no longer created)
        // if (mPrevButton != null) actionsList.add(mPrevButton);
        if (deleteButton != null) actionsList.add(deleteButton);

        Collections.reverse(actionsList);

        collapsedOptions = 0;
        for (FrameLayout action : actionsList) {
            if (visibleOptions - (ViewKt.isVisible(action) ? 1 : 0) + (!ViewKt.isVisible(moreButton) && collapsedOptions > 0 ? 1 : 0) < 8) {
                if (!ViewKt.isVisible(action)) {
                    action.setVisibility(View.VISIBLE);
                    visibleOptions++;
                }
            } else {
                if (ViewKt.isVisible(action)) {
                    action.setVisibility(View.GONE);
                    visibleOptions--;
                }
                collapsedOptions++;
            }
        }
        moreButton.setVisibility(collapsedOptions > 0 ? View.VISIBLE : View.GONE);
    }

    RecordPopup mRecordPopup;

    public void showRecordingOptions(String id, final BaseItemDto program, final boolean recordSeries) {
        if (mRecordPopup == null) {
            int width = Utils.convertDpToPixel(requireContext(), 600);
            Point size = new Point();
            requireActivity().getWindowManager().getDefaultDisplay().getSize(size);
            mRecordPopup = new RecordPopup(requireActivity(), getLifecycle(), mRowsFragment.getView(), (size.x / 2) - (width / 2), mRowsFragment.getView().getTop() + 40, width);
        }
        FullDetailsFragmentHelperKt.getLiveTvSeriesTimer(this, id, response -> {
            if (recordSeries || Utils.isTrue(program.isSports())) {
                mRecordPopup.setContent(requireContext(), program, response, FullDetailsFragment.this, recordSeries);
                mRecordPopup.show();
            } else {
                FullDetailsFragmentHelperKt.createLiveTvSeriesTimer(this, response, () -> {
                    Utils.showToast(requireContext(), R.string.msg_set_to_record);

                    FullDetailsFragmentHelperKt.getLiveTvProgram(this, mProgramInfo.getId(), programInfo -> {
                        setRecTimer(programInfo.getTimerId());
                        return null;
                    });

                    return null;
                });
            }
            return null;
        });
    }


    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(final Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (!(item instanceof BaseRowItem)) return;
            itemLauncher.getValue().launch((BaseRowItem) item, (ItemRowAdapter) ((ListRow) row).getAdapter(), requireContext());
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (!(item instanceof BaseRowItem)) {
                mCurrentItem = null;
            } else {
                mCurrentItem = (BaseRowItem) item;
            }
        }
    }

    private View.OnClickListener markWatchedListener = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            FullDetailsFragmentHelperKt.togglePlayed(FullDetailsFragment.this);
        }
    };

    void shufflePlay() {
        play(mBaseItem, 0, true);
    }

    void play(final BaseItemDto item, final int pos, final boolean shuffle) {
        themeSongs.getValue().stop();
        if (!isAdded()) {
            return;
        }
        playbackHelper.getValue().getItemsToPlay(getContext(), item, pos == 0 && item.getType() == BaseItemKind.MOVIE, shuffle, new Response<List<BaseItemDto>>() {
            @Override
            public void onResponse(List<BaseItemDto> response) {
                if (response.isEmpty()) {
                    return;
                }

                if (!isAdded()) {
                    return;
                }
                KoinJavaComponent.<PlaybackLauncher>get(PlaybackLauncher.class).launch(getContext(), response, pos, false, 0, shuffle);
            }
        });
    }

    void play(final List<BaseItemDto> items, final int pos, final boolean shuffle) {
        themeSongs.getValue().stop();
        if (items.isEmpty()) return;

        if (!isAdded()) {
            return;
        }
        if (shuffle) Collections.shuffle(items);
        KoinJavaComponent.<PlaybackLauncher>get(PlaybackLauncher.class).launch(getContext(), items, pos);
    }

    private void interactWithServerPlugin() {
        if (mBaseItem == null || mBaseItem.getId() == null) {
            Utils.showToast(requireContext(), "Item not available");
            return;
        }

        SubtitleManagementPopup popup = new SubtitleManagementPopup(
            requireContext(),
            getLifecycle(),
            mRowsFragment.getView(),
            mBaseItem
        );
        popup.show();
    }

    private void refreshItemDetails() {
        if (mBaseItem == null || mBaseItem.getId() == null) return;

        FullDetailsFragmentHelperKt.getItem(this, mBaseItem.getId(), updatedItem -> {
            if (updatedItem != null) {
                mBaseItem = updatedItem;
                // Refresh the UI with updated item information
                if (mDorPresenter != null && mDetailsOverviewRow != null) {
                    mDorPresenter.getViewHolder().setItem(mDetailsOverviewRow);
                }
                Utils.showToast(requireContext(), "Item details refreshed");
            }
            return null;
        });
    }

    private void queryAndShowStreams() {
        queryAndShowStreams(0);
    }

    public void queryAndShowStreams(int resumePosition) {
        if (mBaseItem == null) {
            Utils.showToast(requireContext(), "Item not available");
            return;
        }

        Utils.showToast(requireContext(), "Searching for streams...");

        androidx.lifecycle.LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        kotlinx.coroutines.CoroutineScope scope = androidx.lifecycle.LifecycleOwnerKt.getLifecycleScope(lifecycleOwner);
        org.jellyfin.androidtv.ui.itemdetail.StreamScraperHelper helper = new org.jellyfin.androidtv.ui.itemdetail.StreamScraperHelper(
            userPreferences.getValue(),
            scope,
            api.getValue()
        );

        helper.queryStreams(mBaseItem, (java.util.List<org.jellyfin.androidtv.data.scraper.StreamData> streams) -> {
            if (!isAdded()) {
                return kotlin.Unit.INSTANCE;
            }

            if (streams.isEmpty()) {
                Utils.showToast(requireContext(), "No streams found");
                restorePlayButtonState();
                return kotlin.Unit.INSTANCE;
            }

            String mediaTitle = mBaseItem.getName() != null ? mBaseItem.getName() : "Unknown";
            String mediaSubtitle = "";
            if (mBaseItem.getType() == BaseItemKind.EPISODE) {
                if (mBaseItem.getSeriesName() != null && !mBaseItem.getSeriesName().isEmpty()) {
                    mediaSubtitle = mBaseItem.getSeriesName();
                }
                if (mBaseItem.getParentIndexNumber() != null && mBaseItem.getIndexNumber() != null) {
                    if (!mediaSubtitle.isEmpty()) {
                        mediaSubtitle += " - ";
                    }
                    mediaSubtitle += String.format("S%02dE%02d", mBaseItem.getParentIndexNumber(), mBaseItem.getIndexNumber());
                }
                if (mBaseItem.getName() != null && !mBaseItem.getName().isEmpty()) {
                    if (!mediaSubtitle.isEmpty()) {
                        mediaSubtitle += " - ";
                    }
                    mediaSubtitle += mBaseItem.getName();
                }
            } else {
                mediaSubtitle = mediaTitle;
            }

            Timber.d("[FullDetailsFragment] Navigating to StreamSelectionFragment for item ${mBaseItem.getId()}, resumePosition: $resumePosition");

            navigationRepository.getValue().navigate(
                org.jellyfin.androidtv.ui.navigation.Destinations.INSTANCE.streamSelection(
                    mediaTitle,
                    mediaSubtitle,
                    mBaseItem.getId().toString(),
                    resumePosition
                )
            );
            restorePlayButtonState();
            return kotlin.Unit.INSTANCE;
        });
    }

    private void setPlayButtonLoadingState() {
        if (playButton != null) {
            playButton.setIcon(R.drawable.ic_refresh);
            playButton.setLabel(getString(R.string.loading));
        }
    }

    private void restorePlayButtonState() {
        if (playButton != null) {
            playButton.setIcon(playButtonOriginalIcon);
            playButton.setLabel(playButtonOriginalText);
        }
    }

    private void showCollectionsDialog() {
        if (mBaseItem == null || mBaseItem.getId() == null) {
            Utils.showToast(requireContext(), "Item not available");
            return;
        }

        org.jellyfin.sdk.model.api.UserDto currentUser = KoinJavaComponent.<org.jellyfin.androidtv.auth.repository.UserRepository>get(org.jellyfin.androidtv.auth.repository.UserRepository.class).getCurrentUser().getValue();
        if (currentUser == null || currentUser.getId() == null) {
            Utils.showToast(requireContext(), "User not available");
            return;
        }

        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Fetching latest collections list...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new android.os.AsyncTask<Void, Void, java.util.List<String>>() {
            @Override
            protected java.util.List<String> doInBackground(Void... voids) {
                try {
                    org.jellyfin.sdk.api.client.ApiClient apiClient = api.getValue();
                    String baseUrl = apiClient.getBaseUrl();
                    String accessToken = apiClient.getAccessToken();
                    String userId = currentUser.getId().toString();
                    String movieId = mBaseItem.getId().toString();

                    String movieIdNoDashes = movieId.replace("-", "");

                    Timber.d("Looking for collections matching movie ID: %s", movieId);

                    String collectionsUrl = baseUrl + "/Users/" + userId + "/Items?Recursive=true&IncludeItemTypes=BoxSet&Fields=Id,Name";

                    org.json.JSONObject collectionsJson = makeApiRequest(collectionsUrl, accessToken);
                    if (collectionsJson == null) {
                        return null;
                    }

                    org.json.JSONArray collectionsArray = collectionsJson.optJSONArray("Items");
                    if (collectionsArray == null || collectionsArray.length() == 0) {
                        return new java.util.ArrayList<>();
                    }

                    java.util.List<String> foundCollectionNames = new java.util.concurrent.CopyOnWriteArrayList<>();
                    foundCollections = new java.util.concurrent.ConcurrentHashMap<>();
                    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(5);
                    java.util.List<java.util.concurrent.Future<CollectionResult>> futures = new java.util.ArrayList<>();

                    for (int i = 0; i < collectionsArray.length(); i++) {
                        org.json.JSONObject collection = collectionsArray.getJSONObject(i);
                        String collectionId = collection.getString("Id");
                        String collectionName = collection.getString("Name");

                        futures.add(executor.submit(new java.util.concurrent.Callable<CollectionResult>() {
                            @Override
                            public CollectionResult call() {
                                try {
                                    String itemsUrl = baseUrl + "/Users/" + userId + "/Items?ParentId=" + collectionId + "&Fields=Id";

                                    org.json.JSONObject itemsJson = makeApiRequest(itemsUrl, accessToken);

                                    if (itemsJson != null) {
                                        org.json.JSONArray itemsArray = itemsJson.optJSONArray("Items");
                                        if (itemsArray != null) {
                                            for (int j = 0; j < itemsArray.length(); j++) {
                                                org.json.JSONObject item = itemsArray.getJSONObject(j);
                                                String itemId = item.getString("Id");

                                                boolean matchExact = itemId.equals(movieId);
                                                boolean matchNoDashes = itemId.replace("-", "").equals(movieIdNoDashes);
                                                boolean matchLowerCase = itemId.toLowerCase().equals(movieId.toLowerCase());

                                                if (matchExact || matchNoDashes || matchLowerCase) {
                                                    return new CollectionResult(true, collectionName, collectionId);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                }
                                return new CollectionResult(false, null, null);
                            }
                        }));
                    }
                    for (java.util.concurrent.Future<CollectionResult> future : futures) {
                        try {
                            CollectionResult result = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                            if (result.found && result.name != null && result.id != null) {
                                foundCollectionNames.add(result.name);
                                foundCollections.put(result.name, result.id);
                            }
                        } catch (java.util.concurrent.TimeoutException e) {
                            future.cancel(true);
                        } catch (Exception e) {
                        }
                    }

                    executor.shutdown();
                    try {
                        executor.awaitTermination(45, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                    }

                    if (foundCollectionNames.isEmpty()) {
                        Timber.d("Did not find any collections containing movie: %s", mBaseItem.getName());
                    } else {
                        java.util.List<String> collectionDetails = new java.util.ArrayList<>();
                        for (String name : foundCollectionNames) {
                            String id = foundCollections.get(name);
                            collectionDetails.add(name + " :: " + id);
                        }
                        Timber.d("Found collections: %s", collectionDetails.toString());
                    }

                    return new java.util.ArrayList<>(foundCollectionNames);

                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(java.util.List<String> collectionNames) {
                progressDialog.dismiss();

                if (collectionNames == null) {
                    Utils.showToast(requireContext(), "Failed to load collection data");
                    return;
                }

                if (collectionNames.isEmpty()) {
                    new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                            .setTitle("Collections")
                            .setMessage("This item doesn't belong to any collections.")
                            .setPositiveButton(R.string.lbl_ok, null)
                            .show();
                    return;
                }

                showCollectionsListDialog(collectionNames, foundCollections);
            }
        }.execute();
    }

private static class CollectionResult {
    boolean found;
    String name;
    String id;

    CollectionResult(boolean found, String name, String id) {
        this.found = found;
        this.name = name;
        this.id = id;
    }
}

    private org.json.JSONObject makeApiRequest(String url, String accessToken) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-Emby-Token", accessToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream())
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                connection.disconnect();

                return new org.json.JSONObject(response.toString());
            } else {
                Timber.w("API request failed with code %d for: %s", responseCode, url);
                connection.disconnect();
                return null;
            }
        } catch (Exception e) {
            Timber.e(e, "Error making API request to: %s", url);
            return null;
        }
    }

    private void showCollectionsListDialog(List<String> collectionNames, java.util.Map<String, String> foundCollections) {
        if (collectionNames.isEmpty()) {
            new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                    .setTitle("Collections")
                    .setMessage("This item doesn't belong to any collections.")
                    .setPositiveButton(R.string.lbl_ok, null)
                    .show();
            return;
        }

        Collections.sort(collectionNames);

        final String[] collectionNamesArray = collectionNames.toArray(new String[0]);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);
        builder.setTitle("Select Collection");

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                collectionNamesArray
        );

        builder.setAdapter(adapter, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String selectedCollectionName = collectionNamesArray[which];
                String selectedCollectionId = foundCollections.get(selectedCollectionName);

                if (selectedCollectionId != null) {
                    navigateToCollection(selectedCollectionName, selectedCollectionId);
                }
            }
        });

        builder.setNegativeButton(R.string.lbl_cancel, null);
        builder.show();
    }

    private void navigateToCollection(String collectionName, String collectionId) {
        try {
            String formattedCollectionId = collectionId;
            if (collectionId != null && collectionId.length() == 32 && !collectionId.contains("-")) {
                formattedCollectionId = collectionId.substring(0, 8) + "-" +
                                       collectionId.substring(8, 12) + "-" +
                                       collectionId.substring(12, 16) + "-" +
                                       collectionId.substring(16, 20) + "-" +
                                       collectionId.substring(20, 32);
                Timber.d("Converted collection ID from %s to %s", collectionId, formattedCollectionId);
            }

            FullDetailsFragmentHelperKt.getItem(this, java.util.UUID.fromString(formattedCollectionId), collectionItem -> {
                if (collectionItem != null) {
                    navigationRepository.getValue().navigate(Destinations.INSTANCE.collectionBrowser(collectionItem));
                } else {
                    Timber.e("Failed to fetch collection item: %s", collectionName);
                    Utils.showToast(requireContext(), "Failed to open collection");
                }
                return null;
            });
        } catch (Exception e) {
            Timber.e(e, "Error navigating to collection: %s", collectionName);
            Utils.showToast(requireContext(), "Failed to open collection");
        }
    }

    private void showPlaylistActionDialog() {
        if (mBaseItem == null || mBaseItem.getId() == null) {
            Utils.showToast(requireContext(), "Item not available");
            return;
        }

        final String[] actions = {"Add To Existing Playlist", "Create And Add To New Playlist"};

        new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setTitle("Playlist Actions")
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                showExistingPlaylistsDialog();
                                break;
                            case 1:
                                showCreatePlaylistDialog();
                                break;
                        }
                    }
                })
                .setNegativeButton(R.string.lbl_cancel, null)
                .show();
    }

    private void showExistingPlaylistsDialog() {
        if (mBaseItem == null || mBaseItem.getId() == null) {
            Utils.showToast(requireContext(), "Item not available");
            return;
        }

        org.jellyfin.sdk.model.api.UserDto currentUser = KoinJavaComponent.<org.jellyfin.androidtv.auth.repository.UserRepository>get(org.jellyfin.androidtv.auth.repository.UserRepository.class).getCurrentUser().getValue();
        if (currentUser == null || currentUser.getId() == null) {
            Utils.showToast(requireContext(), "User not available");
            return;
        }

        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Fetching playlists...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new android.os.AsyncTask<Void, Void, java.util.List<String>>() {
            @Override
            protected java.util.List<String> doInBackground(Void... voids) {
                try {
                    org.jellyfin.sdk.api.client.ApiClient apiClient = api.getValue();
                    String baseUrl = apiClient.getBaseUrl();
                    String accessToken = apiClient.getAccessToken();
                    String userId = currentUser.getId().toString();

                    String playlistsUrl = baseUrl + "/Users/" + userId + "/Items?Recursive=true&IncludeItemTypes=Playlist&Fields=Id,Name";

                    org.json.JSONObject playlistsJson = makeApiRequest(playlistsUrl, accessToken);
                    if (playlistsJson == null) {
                        return null;
                    }

                    org.json.JSONArray playlistsArray = playlistsJson.optJSONArray("Items");
                    if (playlistsArray == null || playlistsArray.length() == 0) {
                        return new java.util.ArrayList<>();
                    }

                    java.util.List<String> playlistNames = new java.util.ArrayList<>();
                    java.util.Map<String, String> playlistMap = new java.util.HashMap<>();

                    for (int i = 0; i < playlistsArray.length(); i++) {
                        org.json.JSONObject playlist = playlistsArray.getJSONObject(i);
                        String playlistId = playlist.getString("Id");
                        String playlistName = playlist.getString("Name");
                        playlistNames.add(playlistName);
                        playlistMap.put(playlistName, playlistId);
                    }

                    foundPlaylists = playlistMap;
                    return playlistNames;

                } catch (Exception e) {
                    Timber.e(e, "Error fetching playlists");
                    return null;
                }
            }

            @Override
            protected void onPostExecute(java.util.List<String> playlistNames) {
                progressDialog.dismiss();

                if (playlistNames == null) {
                    Utils.showToast(requireContext(), "Failed to load playlists");
                    return;
                }

                if (playlistNames.isEmpty()) {
                    new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                            .setTitle("Playlists")
                            .setMessage("No playlists found. Create a new playlist first.")
                            .setPositiveButton(R.string.lbl_ok, null)
                            .show();
                    return;
                }

                showPlaylistSelectionDialog(playlistNames);
            }
        }.execute();
    }

    private void showPlaylistSelectionDialog(java.util.List<String> playlistNames) {
        if (playlistNames == null || playlistNames.isEmpty()) {
            new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                    .setTitle("Playlists")
                    .setMessage("No playlists found.")
                    .setPositiveButton(R.string.lbl_ok, null)
                    .show();
            return;
        }

        final String[] playlistNamesArray = playlistNames.toArray(new String[0]);

        new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setTitle("Select Playlist")
                .setItems(playlistNamesArray, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selectedPlaylistName = playlistNamesArray[which];
                        String selectedPlaylistId = foundPlaylists.get(selectedPlaylistName);
                        if (selectedPlaylistId != null) {
                            addItemToPlaylist(selectedPlaylistId);
                        }
                    }
                })
                .setNegativeButton(R.string.lbl_cancel, null)
                .show();
    }

    private void addItemToPlaylist(String playlistId) {
        if (playlistId == null || playlistId.trim().isEmpty() || mBaseItem == null || mBaseItem.getId() == null) {
            Utils.showToast(requireContext(), "Invalid playlist or item");
            return;
        }

        org.jellyfin.sdk.model.api.UserDto currentUser = KoinJavaComponent.<org.jellyfin.androidtv.auth.repository.UserRepository>get(org.jellyfin.androidtv.auth.repository.UserRepository.class).getCurrentUser().getValue();
        if (currentUser == null || currentUser.getId() == null) {
            Utils.showToast(requireContext(), "User not available");
            return;
        }

        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Adding item to playlist...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new android.os.AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    org.jellyfin.sdk.api.client.ApiClient apiClient = api.getValue();
                    String baseUrl = apiClient.getBaseUrl();
                    String accessToken = apiClient.getAccessToken();
                    String userId = currentUser.getId().toString();
                    String itemId = mBaseItem.getId().toString();

                    String addToPlaylistUrl = baseUrl + "/Playlists/" + playlistId + "/Items?userId=" + userId + "&ids=" + itemId;

                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(addToPlaylistUrl).openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("X-Emby-Token", accessToken);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(30000);

                    int responseCode = connection.getResponseCode();
                    connection.disconnect();

                    return responseCode == 204;

                } catch (Exception e) {
                    Timber.e(e, "Error adding item to playlist");
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                if (success) {
                    Utils.showToast(requireContext(), "Item added to playlist successfully");
                } else {
                    Utils.showToast(requireContext(), "Failed to add item to playlist");
                }
            }
        }.execute();
    }

    private void showCreatePlaylistDialog() {
        if (mBaseItem == null || mBaseItem.getId() == null) {
            Utils.showToast(requireContext(), "Item not available");
            return;
        }

        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Enter playlist name");
        input.setInputType(android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setTitle("Create New Playlist")
                .setView(input)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String playlistName = input.getText().toString().trim();
                        if (!playlistName.isEmpty()) {
                            createPlaylistWithItem(playlistName);
                        } else {
                            Utils.showToast(requireContext(), "Playlist name cannot be empty");
                        }
                    }
                })
                .setNegativeButton(R.string.lbl_cancel, null)
                .show();
    }

    private void createPlaylistWithItem(String playlistName) {
        if (mBaseItem == null || mBaseItem.getId() == null || playlistName == null || playlistName.trim().isEmpty()) {
            Utils.showToast(requireContext(), "Invalid playlist name or item");
            return;
        }

        org.jellyfin.sdk.model.api.UserDto currentUser = KoinJavaComponent.<org.jellyfin.androidtv.auth.repository.UserRepository>get(org.jellyfin.androidtv.auth.repository.UserRepository.class).getCurrentUser().getValue();
        if (currentUser == null || currentUser.getId() == null) {
            Utils.showToast(requireContext(), "User not available");
            return;
        }

        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Creating playlist...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new android.os.AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    org.jellyfin.sdk.api.client.ApiClient apiClient = api.getValue();
                    String baseUrl = apiClient.getBaseUrl();
                    String accessToken = apiClient.getAccessToken();
                    String userId = currentUser.getId().toString();
                    String itemId = mBaseItem.getId().toString();

                    String createPlaylistUrl = baseUrl + "/Playlists";

                    org.json.JSONObject playlistBody = new org.json.JSONObject();
                    playlistBody.put("Name", playlistName.trim());
                    playlistBody.put("Ids", new org.json.JSONArray(java.util.Arrays.asList(itemId)));
                    playlistBody.put("UserId", userId);
                    playlistBody.put("MediaType", "Video");

                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(createPlaylistUrl).openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("X-Emby-Token", accessToken);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(30000);

                    java.io.OutputStream os = connection.getOutputStream();
                    os.write(playlistBody.toString().getBytes("UTF-8"));
                    os.close();

                    int responseCode = connection.getResponseCode();
                    connection.disconnect();

                    return responseCode == 200;

                } catch (Exception e) {
                    Timber.e(e, "Error creating playlist");
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                if (success) {
                    Utils.showToast(requireContext(), "Playlist created successfully");
                } else {
                    Utils.showToast(requireContext(), "Failed to create playlist");
                }
            }
        }.execute();
    }

    /**
     * Set the anime library from the current item by traversing up the parent chain
     * to find the top-level library (CollectionFolder).
     */
    private void setAnimeLibraryFromCurrentItem() {
        if (mBaseItem == null) {
            Utils.showToast(requireContext(), "Item not available");
            return;
        }

        Utils.showToast(requireContext(), "Finding library...");

        // Get the series item if this is an episode
        UUID itemToCheck = mBaseItem.getId();
        if (mBaseItem.getType() == BaseItemKind.EPISODE && mBaseItem.getSeriesId() != null) {
            itemToCheck = mBaseItem.getSeriesId();
        }

        final UUID finalItemToCheck = itemToCheck;

        // Get current user for API calls
        org.jellyfin.sdk.model.api.UserDto currentUser = KoinJavaComponent.<org.jellyfin.androidtv.auth.repository.UserRepository>get(org.jellyfin.androidtv.auth.repository.UserRepository.class).getCurrentUser().getValue();
        if (currentUser == null || currentUser.getId() == null) {
            Utils.showToast(requireContext(), "User not available");
            return;
        }

        // Use AsyncTask to traverse parent chain
        new android.os.AsyncTask<Void, Void, AnimeLibraryResult>() {
            @Override
            protected AnimeLibraryResult doInBackground(Void... voids) {
                try {
                    org.jellyfin.sdk.api.client.ApiClient apiClient = api.getValue();
                    String baseUrl = apiClient.getBaseUrl();
                    String accessToken = apiClient.getAccessToken();

                    // First, get the item we're checking
                    String userId = currentUser.getId().toString();
                    String itemUrl = baseUrl + "/Users/" + userId + "/Items/" + finalItemToCheck.toString();
                    org.json.JSONObject itemJson = makeApiRequest(itemUrl, accessToken);

                    if (itemJson == null) {
                        Timber.e("Failed to fetch item: %s", finalItemToCheck);
                        return null;
                    }

                    String currentParentId = itemJson.optString("ParentId", null);
                    String lastValidName = null;
                    String lastValidId = null;
                    int depth = 0;
                    int maxDepth = 15;

                    Timber.d("[SetAnimeLibrary] Starting from item: %s, parentId: %s", 
                        itemJson.optString("Name"), currentParentId);

                    // Traverse up the parent chain until we find a CollectionFolder or run out of parents
                    while (currentParentId != null && !currentParentId.isEmpty() && depth < maxDepth) {
                        String parentUrl = baseUrl + "/Users/" + userId + "/Items/" + currentParentId;
                        org.json.JSONObject parentJson = makeApiRequest(parentUrl, accessToken);

                        if (parentJson == null) {
                            Timber.w("Failed to fetch parent at depth %d: %s", depth, currentParentId);
                            break;
                        }

                        String parentType = parentJson.optString("Type", "");
                        String parentName = parentJson.optString("Name", "Unknown");
                        String parentId = parentJson.optString("Id", "");

                        Timber.d("[SetAnimeLibrary] Depth %d: name='%s', type='%s', id='%s'", 
                            depth, parentName, parentType, parentId);

                        // Keep track of the last valid parent (we want the top-level library)
                        lastValidName = parentName;
                        lastValidId = parentId;

                        // Check if this is a CollectionFolder (library)
                        if ("CollectionFolder".equals(parentType) || "UserView".equals(parentType)) {
                            Timber.d("[SetAnimeLibrary] Found library: %s (%s)", parentName, parentId);
                            return new AnimeLibraryResult(parentName, parentId);
                        }

                        // Move up to the next parent
                        currentParentId = parentJson.optString("ParentId", null);
                        depth++;
                    }

                    // If we didn't find a CollectionFolder but have a valid last parent, use that
                    if (lastValidId != null && lastValidName != null) {
                        Timber.d("[SetAnimeLibrary] Using last valid parent as library: %s (%s)", lastValidName, lastValidId);
                        return new AnimeLibraryResult(lastValidName, lastValidId);
                    }

                    return null;

                } catch (Exception e) {
                    Timber.e(e, "Error finding anime library");
                    return null;
                }
            }

            @Override
            protected void onPostExecute(AnimeLibraryResult result) {
                if (result == null || result.id == null) {
                    Utils.showToast(requireContext(), "Could not find library for this item");
                    return;
                }

                // Save the library ID to preferences
                userPreferences.getValue().set(UserPreferences.Companion.getAnimeLibraryId(), result.id);

                // Show toast with library name and ID
                String message = getString(R.string.msg_anime_library_set, result.name, result.id);
                Utils.showToast(requireContext(), message);

                Timber.d("[SetAnimeLibrary] Saved anime library: %s (%s)", result.name, result.id);
            }
        }.execute();
    }

    private static class AnimeLibraryResult {
        String name;
        String id;

        AnimeLibraryResult(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }
}
