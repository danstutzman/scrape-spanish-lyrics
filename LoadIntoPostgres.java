import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class LoadIntoPostgres {
  private static Pattern ESPANOL_PATTERN =
    Pattern.compile("es?p?a[nñ]ol", Pattern.CASE_INSENSITIVE);

  private static Pattern PORTUGUES_PATTERN =
    Pattern.compile("portugu[ée]s", Pattern.CASE_INSENSITIVE);

  private static Pattern INGLES_PATTERN =
    Pattern.compile("ingl[ée]s", Pattern.CASE_INSENSITIVE);

  private static Pattern MUSICA_PATH_PATTERN =
    Pattern.compile("letras.asp\\?letra=([0-9]+)");

  class Song {
    int songId;
    int sourceNum;
    String artistName;
    String songName;
  }

  class Line {
    int songId;
    int lineId;
    int numLineInSong;
    String text;
  }

  class LineWord {
    int songId;
    int lineId;
    int beginIndex;
    int endIndex;
    int numWordInSong;
  }

  private List<Line> lines = new ArrayList<Line>();
  private List<Song> songs = new ArrayList<Song>();
  private Map<Integer, Integer> songIdToSourceNum = new HashMap<Integer, Integer>();
  private Map<String, List<LineWord>> word2LineWords =
    new HashMap<String, List<LineWord>>();

  private void processSongText(JSONObject object, int sourceNum) {
    String path = object.getString("path");

    Song song = new Song();
    song.songId = songs.size() + 1;
    song.sourceNum = sourceNum;
    song.artistName = object.getString("artist_name");
    song.songName = object.getString("song_name");
    songs.add(song);
    songIdToSourceNum.put(song.songId, song.sourceNum);

    int nextNumWordInSong = 0;
    JSONArray songTextLines = object.getJSONArray("song_text");
    for (int l = 0; l < songTextLines.length(); l++) {
      String lineText = songTextLines.getString(l).trim();
      if (true) { //!lineText.equals("")) { // comment to avoid messing up line nums
        Line line = new Line();
        line.songId = song.songId;
        line.lineId = lines.size() + 1;
        line.numLineInSong = l;
        line.text = lineText;
        lines.add(line);

        String lineTextLowercase = lineText.toLowerCase();
        LineWord currentLineWord = null;
        List<LineWord> lineWords = new ArrayList<LineWord>();

        for (int i = 0; i < lineTextLowercase.length(); i++) {
          char c = lineTextLowercase.charAt(i);
          if ((c >= 'a' && c <= 'z') ||
              c == 'ñ' || c == 'á' || c == 'é' || c == 'í' || c == 'ó'
              || c == 'ú' || c == 'ü') {
            if (currentLineWord == null) {
              currentLineWord = new LineWord();
              currentLineWord.songId = song.songId;
              currentLineWord.lineId = line.lineId;
              currentLineWord.beginIndex = i;
              currentLineWord.numWordInSong = nextNumWordInSong;
            }
          } else {
            if (currentLineWord != null) {
              currentLineWord.endIndex = i;
              lineWords.add(currentLineWord);
              currentLineWord = null;
              nextNumWordInSong += 1;
            }
          }
        }
        if (currentLineWord != null) {
          currentLineWord.endIndex = lineTextLowercase.length();
          lineWords.add(currentLineWord);
          currentLineWord = null;
          nextNumWordInSong += 1;
        }

          if (lineText.startsWith("Eso no es para que tú te preocupes")) {
            for (LineWord lineWord : lineWords) {
//              System.out.println("" + lineWord.beginIndex + "," + lineWord.endIndex);
            }
          }


        for (LineWord lineWord : lineWords) {
          String word = 
            lineTextLowercase.substring(lineWord.beginIndex, lineWord.endIndex);
          if (!word2LineWords.containsKey(word)) {
            word2LineWords.put(word, new ArrayList<LineWord>());
          }
          word2LineWords.get(word).add(lineWord);
        }
      }
    }
  }

  public static void main(String[] argv) {
    new LoadIntoPostgres().instanceMain(argv);
  }

  private void instanceMain(String[] argv) {
    if (argv.length < 1) {
      System.err.println("First argument: location of .json file");
      System.exit(1);
    }
    File jsonFile = new File(argv[0]);

    System.err.println("Reading words.en.txt...");
    Set<String> enWords = new HashSet<String>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader("words.en.txt"));
      String line;
      while ((line = reader.readLine()) != null) {
        enWords.add(line.trim().toLowerCase());
      }
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }

    System.err.println("Reading words.es.txt...");
    Set<String> esWords = new HashSet<String>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader("words.es.txt"));
      String line;
      while ((line = reader.readLine()) != null) {
        for (String word : line.trim().toLowerCase().split(" ")) {
          esWords.add(word);
        }
      }
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }

    try {
      BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
      String line;
      Set<Integer> loadedSourceNums = new HashSet<Integer>();
      while ((line = reader.readLine()) != null) {
        JSONObject object = new JSONObject(line);
        if (object.getString("type").equals("song_text")) {
          String path = object.getString("path");
          Matcher matcher = MUSICA_PATH_PATTERN.matcher(path);
          if (!matcher.matches()) {
            throw new RuntimeException("Couldn't parse path '" + path + "'");
          }
          int sourceNum = Integer.parseInt(matcher.group(1));
          String songName = object.getString("song_name");
          if (!songName.equals("") &&
              !ESPANOL_PATTERN.matcher(songName).find() &&
              !PORTUGUES_PATTERN.matcher(songName).find() &&
              !INGLES_PATTERN.matcher(songName).find() &&
              !loadedSourceNums.contains(sourceNum)) {
            loadedSourceNums.add(sourceNum);

            JSONArray songTextLines = object.getJSONArray("song_text");
            int numEnWords = 0;
            int numEsWords = 0;
            int numAllWords = 0;
            for (int l = 0; l < songTextLines.length(); l++) {
              String lineText = songTextLines.getString(l).trim();
              for (String word : lineText.split("[^a-zñáéíóúü]+")) {
                if (enWords.contains(word)) {
                  numEnWords += 1;
                }
                if (esWords.contains(word)) {
                  numEsWords += 1;
                }
                numAllWords += 1;
              }
            }
            if (numEsWords >= numEnWords && numEsWords > numAllWords / 2) {
              processSongText(object, sourceNum);
            }
          }
        }
      }
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }

    Map<String, Integer> word2WordId = new HashMap<String, Integer>();
    int nextWordId = 1;
    for (String word : word2LineWords.keySet()) {
      word2WordId.put(word, nextWordId);
      nextWordId += 1;
    }

    System.out.println("DROP TABLE IF EXISTS songs;");
    System.out.println("CREATE TABLE songs (song_id INT NOT NULL, source_num INT NOT NULL, artist_name TEXT NOT NULL, song_name TEXT NOT NULL);");
    System.out.println("COPY songs FROM STDIN WITH CSV HEADER;");
    System.out.println("song_id,source_num,song_name");
    for (Song song : songs) {
      System.out.println("" + song.songId + "," + song.sourceNum + ",\"" + song.artistName.replaceAll("\"", "\"\"") + "\",\"" + song.songName.replaceAll("\"", "\"\"") + "\"");
    }
    System.out.println("\\.");
    System.out.println("CREATE INDEX idx_songs_song_id ON songs(song_id);");
    System.out.println("CREATE INDEX idx_songs_source_num ON songs(source_num);");
    System.out.println("CREATE INDEX idx_songs_song_name ON songs(song_name);");

    System.out.println("DROP TABLE IF EXISTS lines;");
    System.out.println("CREATE TABLE lines (line_id INT NOT NULL, song_id INT NOT NULL, song_source_num INT NOT NULL, num_line_in_song INT NOT NULL, line TEXT NOT NULL);");
    System.out.println("COPY lines FROM STDIN WITH CSV HEADER;");
    System.out.println("line_id,song_id,song_source_num,num_line_in_song,line");
    for (Line line : lines) {
      System.out.println("" + line.lineId + "," + line.songId + "," + songIdToSourceNum.get(line.songId) + "," + line.numLineInSong + ",\"" + line.text.replaceAll("\"", "\"\"") + "\"");
    }
    System.out.println("\\.");
    System.out.println("create index idx_lines_line_id on lines(line_id);");
    System.out.println("create index idx_lines_song_id on lines(song_id);");
    System.out.println("create index idx_lines_song_source_num_num_line_in_song on lines(song_source_num, num_line_in_song);");

    System.out.println("DROP TABLE IF EXISTS words;");
    System.out.println("CREATE TABLE words (word_id INT NOT NULL, word TEXT NOT NULL);");
    System.out.println("COPY words FROM STDIN WITH CSV HEADER;");
    System.out.println("word_id,word");
    for (String word : word2WordId.keySet()) {
      Integer wordId = word2WordId.get(word);
      System.out.println("" + wordId + "," + word);
    }
    System.out.println("\\.");
    System.out.println("CREATE INDEX idx_words_word_word ON words(word);");

    System.out.println("DROP TABLE IF EXISTS line_words;");
    System.out.println("CREATE TABLE line_words (line_id INT NOT NULL, song_id INT NOT NULL, word_id INT NOT NULL, begin_index SMALLINT NOT NULL, end_index SMALLINT NOT NULL, num_word_in_song SMALLINT NOT NULL);");
    System.out.println("COPY line_words FROM STDIN WITH CSV HEADER;");
    System.out.println("line_id,song_id,word_id,begin_index,end_index,num_word_in_song");

    for (String word : word2WordId.keySet()) {
      Integer wordId = word2WordId.get(word);
      for (LineWord lineWord : word2LineWords.get(word)) {
        System.out.println("" + lineWord.lineId + "," + lineWord.songId + "," +
          wordId + "," + lineWord.beginIndex + "," + lineWord.endIndex +
          "," + lineWord.numWordInSong);
      }
    }
    System.out.println("\\.");
    System.out.println("CREATE INDEX idx_line_words_word_id ON line_words(word_id);");
    System.out.println("CREATE INDEX idx_line_words_song_id_num_word_in_song ON line_words(song_id, num_word_in_song);");
  }
}
