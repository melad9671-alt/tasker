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
        player.MediaEnded += (_, _) => Dispatcher.Invoke(Next_Click);
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
    private void PlaylistsNav_Click(object sender, RoutedEventArgs e) => MessageBox.Show("Playlist management is coming in the next desktop milestone.", "Sonora Music", MessageBoxButton.OK, MessageBoxImage.Information);

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
        catch (Exception ex) { MessageBox.Show($"Couldn't play this file.\n{ex.Message}", "Sonora Music"); }
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
        if ((sender as Button)?.Tag is not Track track) return;
        if (!favorites.Add(track.Path)) favorites.Remove(track.Path);
        track.Favorite = favorites.Contains(track.Path);
        if (currentPage == "Favorites") ApplyFilter();
    }
    private void Theme_Click(object sender, RoutedEventArgs e)
    {
        dark = !dark;
        Background = dark ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(11,23,21)) : System.Windows.Media.Brushes.WhiteSmoke;
    }
    private void Settings_Click(object sender, RoutedEventArgs e) =>
        MessageBox.Show("Choose your music folder from the button at the bottom left. More appearance controls will be added here.", "Sonora Music");
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
