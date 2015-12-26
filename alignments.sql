ALTER TABLE songs ADD COLUMN youtube_video_id varchar(255);

UPDATE songs
SET youtube_video_id = 'SqWrliMzrW8'
WHERE LOWER(song_name) = LOWER('Yo me enamoré');

DROP TABLE IF EXISTS alignments;
CREATE TABLE alignments (
  alignment_id SERIAL,
  song_id INT NOT NULL,
  begin_seconds FLOAT NOT NULL,
  end_seconds FLOAT NOT NULL,
  begin_num_word_in_song SMALLINT NOT NULL,
  end_num_word_in_song SMALLINT NOT NULL,
  result_json TEXT
);
INSERT INTO alignments (song_id,
  begin_seconds, end_seconds,
  begin_num_word_in_song, end_num_word_in_song)
  VALUES ((SELECT song_id FROM songs WHERE LOWER(song_name) = LOWER('Yo me enamoré')),
    0, 10,
    0, 20);
