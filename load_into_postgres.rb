# encoding: UTF-8
require 'json'

class Song
  attr_accessor :song_id
  attr_accessor :artist_name
  attr_accessor :song_name
  def initialize(song_id, artist_name, song_name)
    @song_id     = song_id
    @artist_name = artist_name
    @song_name   = song_name
  end
end

class Line
  attr_accessor :song_id
  attr_accessor :line_id
  attr_accessor :text
  def initialize(song_id, line_id, text)
    @song_id = song_id
    @line_id = line_id
    @text    = text
  end
end

class LineWord
  attr_accessor :word
  attr_accessor :line_id
  attr_accessor :begin_index
  attr_accessor :end_index
  attr_accessor :num_word_in_song
end

lines = []
songs = []
word2line_words = {}
File.open('akwid.txt') do |infile|
  infile.each_line do |json|
    object = JSON.parse(json)
    if object['type'] == 'song_text'
      song = Song.new(songs.size + 1, object['artist_name'], object['song_name'])
      songs.push song
      next_num_word_in_song = 0
      object['song_text'].each do |line_text|
        line_text.strip!
        if line_text != ''
          line = Line.new(song.song_id, lines.size + 1, line_text)
          lines.push line

          line_words = []
          current_line_word = nil
          line_text.chars.each_with_index do |char, i|
            if char.match /[a-zñáéíóúü]/i
              if current_line_word == nil
                current_line_word = LineWord.new
                current_line_word.line_id = line.line_id
                current_line_word.begin_index = i
                current_line_word.word = ''
                current_line_word.num_word_in_song = next_num_word_in_song
              end
              current_line_word.word += char
            else
              if current_line_word
                current_line_word.end_index = i
                line_words.push current_line_word
                current_line_word = nil
                next_num_word_in_song += 1
              end
            end
          end
          if current_line_word
            current_line_word.end_index = line_text.chars.size
            line_words.push current_line_word
            current_line_word = nil
            next_num_word_in_song += 1
          end

          line_words.each do |line_word|
            word = line_word.word.downcase
            if word2line_words[word] == nil
              word2line_words[word] = []
            end
            word2line_words[word].push line_word
          end
        end
      end
    end
  end
end

word2word_id = {}
word2line_words.keys.each_with_index do |word, word_id0|
  word2word_id[word] = word_id0 + 1
end

puts "DROP TABLE IF EXISTS lines;
CREATE TABLE lines (
  line_id int not null,
  song_id int not null,
  line varchar(500) not null
);
COPY lines FROM STDIN WITH CSV HEADER;
line_id,song_id,line"
lines.each_with_index do |line, line_id0|
  line_text = line.text.gsub('"', '""')
  puts "#{line_id0 + 1},#{line.song_id},\"#{line_text}\""
end
puts "\\."

puts "DROP TABLE IF EXISTS words;
CREATE TABLE words (
  word_id int not null,
  word varchar(255) not null
);
COPY words FROM STDIN WITH CSV HEADER;
word_id,word"
word2word_id.each do |word, word_id|
  puts "#{word_id},\"#{word}\""
end
puts "\\."

puts "CREATE INDEX idx_words_word_word ON words(word);"

puts "DROP TABLE IF EXISTS line_words;
CREATE TABLE line_words (
  line_id int not null,
  word_id int not null,
  begin_index smallint not null,
  end_index smallint not null,
  num_word_in_song smallint not null
);
COPY line_words FROM STDIN WITH CSV HEADER;
line_id,word_id,begin_index,end_index,num_word_in_song"
word2word_id.each do |word, word_id|
  word2line_words[word].each do |line_word|
    puts "#{line_word.line_id},#{word_id},#{line_word.begin_index},#{line_word.end_index},#{line_word.num_word_in_song}"
  end
end
puts "\\."

puts "CREATE INDEX idx_line_words_word_id ON line_words(word_id);"

puts "DROP TABLE IF EXISTS songs;
CREATE TABLE songs (
  song_id int not null,
  song_name varchar(255) not null
);
COPY songs FROM STDIN WITH CSV HEADER;
song_id,song_name"
songs.each do |song|
  song_name = song.song_name.gsub('"', '""')
  puts "#{song.song_id},\"#{song_name}\""
end
puts "\\."
