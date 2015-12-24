# encoding: UTF-8
require 'json'

lines = []
word2line_ids = {}
File.open('akwid.txt') do |infile|
  infile.each_line do |json|
    object = JSON.parse(json)
    if object['type'] == 'song_text'
      object['song_text'].each do |line|
        line.strip!
        if line != ''
          lines.push line
          line_id = lines.size

          words = line.downcase.split(/[ ,.?()!?"*:¿?\[\]¡\/]+/)
          words.uniq.each do |word|
            if word2line_ids[word] == nil
              word2line_ids[word] = []
            end
            word2line_ids[word].push line_id
          end
        end
      end
    end
  end
end

word2word_id = {}
word2line_ids.keys.each_with_index do |word, word_id0|
  word2word_id[word] = word_id0 + 1
end

puts "DROP TABLE IF EXISTS lines;
CREATE TABLE lines (
  line_id int not null,
  line varchar(500) not null
);
COPY lines FROM STDIN WITH CSV HEADER;
line_id,line"
lines.each_with_index do |line, line_id0|
  line = line.gsub('"', '""')
  puts "#{line_id0 + 1},\"#{line}\""
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

puts "DROP TABLE IF EXISTS words_lines;
CREATE TABLE words_lines (
  word_id int not null,
  line_id int not null
);
COPY words_lines FROM STDIN WITH CSV HEADER;
word_id,line_id"
word2word_id.each do |word, word_id|
  word2line_ids[word].each do |line_id|
    puts "#{word_id},#{line_id}"
  end
end
puts "\\."
