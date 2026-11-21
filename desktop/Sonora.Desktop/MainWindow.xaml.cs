using System.Collections.ObjectModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.Win32;
using Forms = System.Windows.Forms;

namespace Sonora.Desktop;

public sealed class Track
{
    public int Number { get; set; }
    public string Title { get; set; } = "";
    public string Artist { get; set; } = "Unknown artist";
    public string Album { get; set; } = "Unknown album";
    public string Duration { get; set; } = "—:—";
    public string Path { get; set; } = "";
    public bool Favorite { get; set; }
}
public partial class MainWindow : Window
{
    private readonly ObservableCollection<Track> allTracks = new();
    private readonly ObservableCollection<Track> visibleTracks = new();
    private readonly HashSet<string> favorites = new(StringComparer.OrdinalIgnoreCase);
    private readonly MediaPlayer player = new();
    private readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromMilliseconds(500) };
    private bool dark = true, changingSlider;
    private int currentIndex = -1;
    private string currentPage = "Songs";
    private string? musicFolder;

    public MainWindow()
    {
        InitializeComponent();
        TrackList.ItemsSource = visibleTracks;
        timer.Tick += (_, _) => UpdateProgress();
        timer.Start();
        player.MediaEnded += (_, _) => Dispatcher.Invoke(() => Next_Click(this, new RoutedEventArgs()));
        Loaded += (_, _) => { };
    }

    private void ChooseFolder_Click(object sender, RoutedEventArgs e)
    {
        using var dialog = new Forms.FolderBrowserDialog { Description = "Choose your music folder", UseDescriptionForTitle = true };
        if (dialog.ShowDialog() == Forms.DialogResult.OK) LoadFolder(dialog.SelectedPath);
    }

    private void LoadFolder(string folder)
    {
        musicFolder = folder;
        FolderLabel.Text = folder;
        allTracks.Clear();
        var files = Directory.EnumerateFiles(folder, "*.*", SearchOption.AllDirectories)
            .Where(p => new[] { ".mp3", ".wav", ".wma", ".m4a", ".aac", ".flac" }.Contains(System.IO.Path.GetExtension(p), StringComparer.OrdinalIgnoreCase))
            .OrderBy(p => System.IO.Path.GetFileNameWithoutExtension(p), StringComparer.CurrentCultureIgnoreCase).ToList();
        var number = 1;
        foreach (var file in files)
        {
            var name = System.IO.Path.GetFileNameWithoutExtension(file);
            var parts = name.Split(" - ", 2, StringSplitOptions.TrimEntries);
            allTracks.Add(new Track { Number = number++, Title = parts.Length > 1 ? parts[1] : name, Artist = parts.Length > 1 ? parts[0] : "Unknown artist", Album = "—", Path = file, Favorite = favorites.Contains(file) });
        }
        ApplyFilter();
        TrackCount.Text = $"{allTracks.Count} TRACKS";
        FolderHint.Text = allTracks.Count == 0 ? "No supported audio files found" : "Your offline library is ready";
    }

    private void ApplyFilter()
    {
        visibleTracks.Clear();
        IEnumerable<Track> source = allTracks;
        if (currentPage == "Favorites") source = source.Where(t => favorites.Contains(t.Path));
        if (currentPage == "Albums") source = source.GroupBy(t => t.Album).Select(g => g.First());
        if (currentPage == "Artists") source = source.GroupBy(t => t.Artist).Select(g => g.First());
        var q = SearchBox?.Text?.Trim();
        if (!string.IsNullOrEmpty(q)) source = source.Where(t => $"{t.Title} {t.Artist} {t.Album}".Contains(q, StringComparison.CurrentCultureIgnoreCase));
        foreach (var track in source) visibleTracks.Add(track);
        PageTitle.Text = currentPage == "Songs" ? "Your library" : currentPage;
        TrackCount.Text = $"{visibleTracks.Count} ITEMS";
    }

    private void SearchBox_TextChanged(object sender, TextChangedEventArgs e) => ApplyFilter();
    private void ShowPage(string page) { currentPage = page; ApplyFilter(); }
    private void SongsNav_Click(object sender, RoutedEventArgs e) => ShowPage("Songs");
    private void AlbumsNav_Click(object sender, RoutedEventArgs e) => ShowPage("Albums");
    private void ArtistsNav_Click(object sender, RoutedEventArgs e) => ShowPage("Artists");
    private void FavoritesNav_Click(object sender, RoutedEventArgs e) => ShowPage("Favorites");
    private void PlaylistsNav_Click(object sender, RoutedEventArgs e) => System.Windows.MessageBox.Show("Playlist management is coming in the next desktop milestone.", "Sonora Music", MessageBoxButton.OK, MessageBoxImage.Information);

    private void TrackList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (TrackList.SelectedItem is Track track) PlayTrack(track);
    }
    private void PlayTrack(Track track)
    {
        currentIndex = allTracks.IndexOf(track);
        try
        {
            player.Open(new Uri(track.Path));
            player.Play();
            NowTitle.Text = track.Title;
            NowArtist.Text = track.Artist;
            PlayButton.Content = "Ⅱ";
        }
        catch (Exception ex) { System.Windows.MessageBox.Show($"Couldn't play this file.\n{ex.Message}", "Sonora Music"); }
    }
    private void Play_Click(object sender, RoutedEventArgs e)
    {
        if (currentIndex < 0) { if (visibleTracks.Count > 0) PlayTrack(visibleTracks[0]); return; }
        if (PlayButton.Content?.ToString() == "Ⅱ") { player.Pause(); PlayButton.Content = "▶"; }
        else { player.Play(); PlayButton.Content = "Ⅱ"; }
    }
    private void Next_Click(object sender, RoutedEventArgs e)
    {
        if (allTracks.Count == 0) return;
        currentIndex = (currentIndex + 1) % allTracks.Count;
        PlayTrack(allTracks[currentIndex]);
    }
    private void Previous_Click(object sender, RoutedEventArgs e)
    {
        if (allTracks.Count == 0) return;
        currentIndex = currentIndex <= 0 ? allTracks.Count - 1 : currentIndex - 1;
        PlayTrack(allTracks[currentIndex]);
    }
    private void Favorite_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as System.Windows.Controls.Button)?.Tag is not Track track) return;
        if (!favorites.Add(track.Path)) favorites.Remove(track.Path);
        track.Favorite = favorites.Contains(track.Path);
        var button = (System.Windows.Controls.Button)sender;
        button.ToolTip = track.Favorite ? "Remove from favorites" : "Add to favorites";
        button.Content = new System.Windows.Shapes.Path
        {
            Data = Geometry.Parse("M12,1 L15.3,8 L23,9 L17.5,14.4 L18.8,22 L12,18.4 L5.2,22 L6.5,14.4 L1,9 L8.7,8 Z"),
            Fill = new SolidColorBrush((Color)ColorConverter.ConvertFromString(track.Favorite ? "#F5C451" : "#19CDB6")),
            Width = 16, Height = 16, Stretch = Stretch.Uniform
        };
        if (currentPage == "Favorites") ApplyFilter();
    }
    private void Theme_Click(object sender, RoutedEventArgs e)
    {
        dark = !dark;
        var palette = System.Windows.Application.Current.Resources;
        void Set(string key, string color) => palette[key] = new SolidColorBrush((Color)ColorConverter.ConvertFromString(color));
        Set("WindowBackground", dark ? "#0B1715" : "#F3F8F6");
        Set("SidebarBackground", dark ? "#101F1C" : "#E7F0EC");
        Set("SurfaceBackground", dark ? "#101F1C" : "#FFFFFF");
        Set("SearchBackground", dark ? "#172925" : "#E5EFEB");
        Set("PlayerBackground", dark ? "#142521" : "#E7F0EC");
        Set("PrimaryText", dark ? "#EDF7F3" : "#18332D");
        Set("SecondaryText", dark ? "#91A9A2" : "#58736A");
        Set("MutedText", dark ? "#78948C" : "#71877F");
        Set("HoverSurface", dark ? "#1B302B" : "#DCEAE4");
        Set("SelectedSurface", dark ? "#1B3932" : "#CFE7DD");
        Background = (Brush)palette["WindowBackground"];
        ThemeButton.Content = dark ? "☀" : "☾";
    }
    private void Settings_Click(object sender, RoutedEventArgs e) =>
        System.Windows.MessageBox.Show("Choose your music folder from the button at the bottom left. More appearance controls will be added here.", "Sonora Music");
    private void UpdateProgress()
    {
        if (!player.NaturalDuration.HasTimeSpan || player.NaturalDuration.TimeSpan.TotalSeconds <= 0) return;
        changingSlider = true;
        ProgressSlider.Maximum = player.NaturalDuration.TimeSpan.TotalSeconds;
        ProgressSlider.Value = Math.Min(player.Position.TotalSeconds, ProgressSlider.Maximum);
        changingSlider = false;
    }
    private void ProgressSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (!changingSlider && player.NaturalDuration.HasTimeSpan) player.Position = TimeSpan.FromSeconds(e.NewValue);
    }
}
