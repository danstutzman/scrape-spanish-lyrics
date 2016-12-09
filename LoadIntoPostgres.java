import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
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
    List<LineWord> lineWords;
  }

  class LineWord {
    int songId;
    int lineId;
    int beginCharPunctuation;
    Integer beginCharHighlight;
    Integer endCharHighlight;
    int endCharPunctuation;
    int numWordInSong;
    String wordLowercase;
    String partOfSpeech;
    String lemma;
  }

  private List<Line> lines = new ArrayList<Line>();
  private List<Song> songs = new ArrayList<Song>();
  private Map<Integer, Integer> songIdToSourceNum = new HashMap<Integer, Integer>();
  private Map<String, List<LineWord>> word2LineWords =
    new HashMap<String, List<LineWord>>();

  private void processSongText(JSONObject object, int sourceNum, File lemmaDir) {
    Song song = new Song();
    song.songId = songs.size() + 1;
    song.sourceNum = sourceNum;
    song.artistName = object.getString("artist_name");
    song.songName = object.getString("song_name");
    songs.add(song);
    songIdToSourceNum.put(song.songId, song.sourceNum);

    List<String[]> lemmaLines = new ArrayList<String[]>();
    File lemmaFile;
    try {
      lemmaFile = new File(lemmaDir, "" + sourceNum + ".out");
      if (!lemmaFile.exists()) {
        throw new RuntimeException("Can't find " + lemmaFile.getAbsolutePath());
      }

      BufferedReader lemmaReader = new BufferedReader(new FileReader(
        new File(lemmaDir, "" + sourceNum + ".out")));
      String lemmaLine;
      while ((lemmaLine = lemmaReader.readLine()) != null) {
        String[] values = lemmaLine.split(" ");
        for (String wordBetweenPunctuation : values[0].replace("_%", "").split("(_|\\.|-|'|,|:)")) {
          String[] valuesClone = Arrays.copyOf(values, values.length);
          valuesClone[0] = wordBetweenPunctuation;
          lemmaLines.add(valuesClone);
        }
      }
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }

    List<LineWord> lineWords = new ArrayList<LineWord>();
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
        line.lineWords = new ArrayList<LineWord>();
        lines.add(line);

        String lineTextLowercase = lineText.toLowerCase();
        LineWord currentLineWord = null;
        List<LineWord> thisLinesLineWords = new ArrayList<LineWord>();
        for (int i = 0; i < lineTextLowercase.length(); i++) {
          char c = lineTextLowercase.charAt(i);
          if ((c >= 'a' && c <= 'z') ||
              c == 'ñ' || c == 'á' || c == 'é' || c == 'í' || c == 'ó'
              || c == 'ú' || c == 'ü' || (c >= '0' && c <= '9')) {
            if (currentLineWord == null) {
              currentLineWord = new LineWord();
              currentLineWord.songId = song.songId;
              currentLineWord.lineId = line.lineId;
              currentLineWord.beginCharPunctuation = i;
              currentLineWord.beginCharHighlight = i;
              currentLineWord.numWordInSong = nextNumWordInSong;
            } else {
              if (currentLineWord.beginCharHighlight == null) {
                // if we started the word punctuation but not the a-z yet
                currentLineWord.beginCharHighlight = i;
              } else if (currentLineWord.endCharHighlight != null) {
                // if there's end punctuation already in this word
                currentLineWord.endCharPunctuation = i;
                thisLinesLineWords.add(currentLineWord);
                line.lineWords.add(currentLineWord);
                currentLineWord = null;
                nextNumWordInSong += 1;

                currentLineWord = new LineWord();
                currentLineWord.songId = song.songId;
                currentLineWord.lineId = line.lineId;
                currentLineWord.beginCharPunctuation = i;
                currentLineWord.beginCharHighlight = i;
                currentLineWord.numWordInSong = nextNumWordInSong;
              }
            }
          } else if (c == ' ') {
            if (currentLineWord != null) {
              if (currentLineWord.beginCharHighlight != null) {
                // end the word
                if (currentLineWord.endCharHighlight == null) {
                  currentLineWord.endCharHighlight = i;
                }
                currentLineWord.endCharPunctuation = i;
                thisLinesLineWords.add(currentLineWord);
                line.lineWords.add(currentLineWord);
                currentLineWord = null;
                nextNumWordInSong += 1;
              } else {
                // word didn't contain non-punctuation; throw it out
                currentLineWord = null;
              }
            }
          } else { // some kind of punctuation
            if (currentLineWord == null) {
              currentLineWord = new LineWord();
              currentLineWord.songId = song.songId;
              currentLineWord.lineId = line.lineId;
              currentLineWord.beginCharPunctuation = i;
              currentLineWord.numWordInSong = nextNumWordInSong;
            } else {
              // end the highlight but not the word
              if (currentLineWord.beginCharHighlight != null &&
                  currentLineWord.endCharHighlight == null) {
                currentLineWord.endCharHighlight = i;
              }
            }
          }
        }
        if (currentLineWord != null && currentLineWord.beginCharHighlight != null) {
          if (currentLineWord.endCharHighlight == null) {
            currentLineWord.endCharHighlight = lineTextLowercase.length();
          }
          currentLineWord.endCharPunctuation = lineTextLowercase.length();
          thisLinesLineWords.add(currentLineWord);
          line.lineWords.add(currentLineWord);
          currentLineWord = null;
          nextNumWordInSong += 1;
        }

        for (LineWord lineWord : thisLinesLineWords) {
          lineWord.wordLowercase = lineTextLowercase.substring(
            lineWord.beginCharHighlight, lineWord.endCharHighlight);
        }

        lineWords.addAll(thisLinesLineWords);
      } // end if true
    } // next line

    int lastLemmaLineNum = -1;
    for (LineWord lineWord : lineWords) {

      lastLemmaLineNum += 1;
      String[] lemmaLine = lemmaLines.get(lastLemmaLineNum);
      // Parts of speech starting with F are punctuation-only
      while (lemmaLine[0].equals("") ||
          (lemmaLine[2].startsWith("F") && !lemmaLine[0].equals("etc"))) {
        lastLemmaLineNum += 1;
        if (lemmaLines.size() <= lastLemmaLineNum) {
          throw new RuntimeException("Ran out of " + lemmaFile.getAbsolutePath());
        }
        lemmaLine = lemmaLines.get(lastLemmaLineNum);
      }
      String lemmaWord = lemmaLine[0].toLowerCase();
      if (!lineWord.wordLowercase.equals(lemmaWord)) {
        if (equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "lo") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "la") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "le") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "las") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "los") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "les") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "me") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "te") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "se") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "nos") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "os") ||
            false) {
          // ignore cases like perderla being split into perder and la
          lastLemmaLineNum += 1;
        } else if (equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "melo") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "mela") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "melos") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "melas") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "telo") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "tela") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "telos") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "telas") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "selo") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "sela") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "selos") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "selas") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "seme") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "sete") ||
            equalsIgnoreAccent(lineWord.wordLowercase, lemmaWord + "sele") ||
            false) {
          lastLemmaLineNum += 2;
        } else if (lineWord.wordLowercase.endsWith("aos") &&
            lemmaWord.endsWith("ad")) {
          // jorobaos -> jorobad os?
          lastLemmaLineNum += 1;
        } else if (lemmaWord.equals(lineWord.wordLowercase + "_de")) {
          // dentro de -> dentro_de
          lastLemmaLineNum -= 1;
        } else if (lemmaWord.endsWith("_donde")) {
          lastLemmaLineNum -= 1;
        } else if (lineWord.wordLowercase.equals("del") &&
            lemmaWord.endsWith("de")) {
          // del -> de el
          lastLemmaLineNum += 1;
        } else if (lineWord.wordLowercase.equals("al") &&
            lemmaWord.endsWith("a")) {
          // al -> a el
          lastLemmaLineNum += 1;
        } else if (lineWord.wordLowercase.endsWith("onos")) {
          // vamonos -> vamos nos
          lastLemmaLineNum += 1;
        } else if (lemmaWord.contains("ä") ||
                   lemmaWord.contains("º") ||
                   lemmaWord.contains("$") ||
                   lemmaWord.contains("/") ||
                   lemmaWord.contains("ﬁ") ||
                   lemmaWord.contains("ç") ||
                   lemmaWord.contains("\u0441") ||
                   lemmaWord.contains("â") ||
                   lemmaWord.contains("ª") ||
                   lemmaWord.contains("#") ||
                   lemmaWord.contains("ã") ||
                   lemmaWord.contains("ù") ||
                   lemmaWord.contains("+") ||
                   lemmaWord.contains("ï") ||
                   lemmaWord.contains("ë") ||
                   lemmaWord.contains("*") ||
                   lemmaWord.contains("@") ||
                   lemmaWord.contains("ℓα")) {
          // backwards accent mark or other unconvertible
          lastLemmaLineNum -= 1;
        } else if (lemmaWord.equals(lineWord.wordLowercase + ".")) {
          // interpreting mar. (at end of sentence) as abbreviation for marzo
        } else if (lemmaWord.equals(lineWord.wordLowercase + "ã")) {
          // probably poorly converted UTF-8 text input
          lastLemmaLineNum += 1;
        } else if (lemmaWord.equals(lineWord.wordLowercase.replace("á", "a")) ||
                  (lemmaWord.substring(0, lemmaWord.length() - 1) + "os").equals(
                    lineWord.wordLowercase.replace("á", "a"))) {
          // interpreted as vestid + os
          lastLemmaLineNum += 1;
        } else {
          throw new RuntimeException("Expected '" + lineWord.wordLowercase + "' but found '" + lemmaWord + "' in " + lemmaFile.getAbsolutePath() + " line " + lastLemmaLineNum);
        }
      }
      lineWord.partOfSpeech = lemmaLine[2];
      lineWord.lemma = lemmaLine[1];

      if (!word2LineWords.containsKey(lineWord.wordLowercase)) {
        word2LineWords.put(lineWord.wordLowercase, new ArrayList<LineWord>());
      }
      word2LineWords.get(lineWord.wordLowercase).add(lineWord);
    }
  }

  private static boolean equalsIgnoreAccent(String s1, String s2) {
    if (!s1.equals(s1.toLowerCase())) {
      throw new RuntimeException("Non-lowercase input: " + s1);
    }
    if (!s2.equals(s2.toLowerCase())) {
      throw new RuntimeException("Non-lowercase input: " + s2);
    }
    s1 = s1.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
    s2 = s2.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
    return s1.equals(s2);
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

    if (argv.length < 2) {
      System.err.println("Second argument: directory with FreeLing lemma output");
      System.exit(1);
    }
    File lemmaDir = new File(argv[1]);

    if (argv.length < 3) {
      System.err.println("Third argument: directory with myfreeling dir");
      System.exit(1);
    }
    File myfreelingParentDir = new File(argv[2]);

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
          if (songName.equals("")) {
            System.err.println("Skipping " + sourceNum + " because blank");
          } else if (ESPANOL_PATTERN.matcher(songName).find() ||
              PORTUGUES_PATTERN.matcher(songName).find() ||
              INGLES_PATTERN.matcher(songName).find()) {
            System.err.println("Skipping " + songName + " because name");
          } else if (loadedSourceNums.contains(sourceNum)) {
            System.err.println("Skipping " + sourceNum + " because already loaded");
          } else {
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
              // Generate lemmas if not done yet
              File lemmaFile = new File(lemmaDir, "" + sourceNum + ".out");
              if (!lemmaFile.exists() || lemmaFile.length() == 0) {
                String[] lemmatizerClientCommand = {
                  myfreelingParentDir.getAbsolutePath() +
                    "/myfreeling/src/main/analyzer_client", "3001" };
                Process clientChild =
                  Runtime.getRuntime().exec(lemmatizerClientCommand);

                BufferedWriter clientWriter = new BufferedWriter(
                  new OutputStreamWriter(clientChild.getOutputStream()));
                for (int l = 0; l < songTextLines.length(); l++) {
                  String lineText = songTextLines.getString(l).trim();
                  clientWriter.write(lineText);
                  clientWriter.write("\n");
                }
                clientWriter.close();

                try { clientChild.waitFor(); } catch (InterruptedException e) {}

                BufferedReader clientErrorReader = new BufferedReader(
                  new InputStreamReader(clientChild.getErrorStream()));
                if (clientErrorReader.ready()) {
                  String clientErrorLine = clientErrorReader.readLine();
                  System.err.println("Client error: " + clientErrorLine);
                  while (clientErrorLine != null) {
                    System.err.println("Client error: " + clientErrorLine);
                    clientErrorLine = clientErrorReader.readLine();
                  }
                }

                BufferedReader clientReader = new BufferedReader(
                  new InputStreamReader(clientChild.getInputStream()));
                BufferedWriter lemmaFileWriter = new BufferedWriter(
                  new OutputStreamWriter(new FileOutputStream(lemmaFile)));
                String line3 = clientReader.readLine();
                while (line3 != null) {
                  lemmaFileWriter.write(line3);
                  lemmaFileWriter.write("\n");
                  line3 = clientReader.readLine();
                }

                lemmaFileWriter.close();
                System.err.println("Wrote lemmas to " + lemmaFile);
              }

              processSongText(object, sourceNum, lemmaDir);
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
    System.out.println("CREATE TABLE lines (line_id INT NOT NULL, song_id INT NOT NULL, song_source_num INT NOT NULL, num_line_in_song INT NOT NULL, line TEXT NOT NULL, line_words_json TEXT NOT NULL);");
    System.out.println("COPY lines FROM STDIN WITH CSV HEADER;");
    System.out.println("line_id,song_id,song_source_num,num_line_in_song,line,line_words_json");
    for (Line line : lines) {
      JSONArray lineWordsJson = new JSONArray();
      for (LineWord lineWord : line.lineWords) {
        JSONObject lineWordJson = new JSONObject();
        lineWordJson.put("begin_char_punctuation", lineWord.beginCharPunctuation);
        lineWordJson.put("begin_char_highlight", lineWord.beginCharHighlight);
        lineWordJson.put("end_char_highlight", lineWord.endCharHighlight);
        lineWordJson.put("end_char_punctuation", lineWord.endCharPunctuation);
        lineWordJson.put("num_word_in_song", lineWord.numWordInSong);
        lineWordJson.put("word_lowercase", lineWord.wordLowercase);
        lineWordJson.put("part_of_speech", lineWord.partOfSpeech);
        lineWordJson.put("lemma", lineWord.lemma);
        lineWordsJson.put(lineWordJson);
      }
      System.out.println("" + line.lineId + "," + line.songId + "," + songIdToSourceNum.get(line.songId) + "," + line.numLineInSong + ",\"" + line.text.replaceAll("\"", "\"\"") + "\",\"" + lineWordsJson.toString().replaceAll("\"", "\"\"") + "\"");
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
    System.out.println("CREATE INDEX idx_words_word_word_id ON words(word_id);");

    System.out.println("DROP TABLE IF EXISTS line_words;");
    System.out.println("CREATE TABLE line_words (line_id INT NOT NULL, song_id INT NOT NULL, word_id INT NOT NULL, begin_char_punctuation SMALLINT NOT NULL, begin_char_highlight SMALLINT NOT NULL, end_char_highlight SMALLINT NOT NULL, end_char_punctuation SMALLINT NOT NULL, word_lowercase TEXT NOT NULL, num_word_in_song SMALLINT NOT NULL, part_of_speech TEXT NOT NULL, lemma TEXT NOT NULL);");
    System.out.println("COPY line_words FROM STDIN WITH CSV HEADER;");
    System.out.println("line_id,song_id,word_id,begin_char_punctuation,begin_char_highlight,end_char_highlight,end_char_punctuation,word_lowercase,num_word_in_song,part_of_speech,lemma");

    for (String word : word2WordId.keySet()) {
      Integer wordId = word2WordId.get(word);
      for (LineWord lineWord : word2LineWords.get(word)) {
        System.out.println("" +
          lineWord.lineId + "," +
          lineWord.songId + "," +
          wordId + "," +
          lineWord.beginCharPunctuation + "," +
          lineWord.beginCharHighlight + "," +
          lineWord.endCharHighlight + "," +
          lineWord.endCharPunctuation + "," +
          "\"" + lineWord.wordLowercase + "\"," +
          lineWord.numWordInSong + "," +
          lineWord.partOfSpeech + "," +
          "\"" + lineWord.lemma + "\"");
      }
    }
    System.out.println("\\.");
    System.out.println("CREATE INDEX idx_line_words_line_id ON line_words(line_id);");
    System.out.println("CREATE INDEX idx_line_words_word_id ON line_words(word_id);");
    System.out.println("CREATE INDEX idx_line_words_song_id_num_word_in_song ON line_words(song_id, num_word_in_song);");
  }
}
