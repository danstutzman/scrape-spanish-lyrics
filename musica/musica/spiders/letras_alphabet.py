# -*- coding: utf-8 -*-
import re
import scrapy

class LetrasAlphabetSpider(scrapy.Spider):
  name = "letras_alphabet"
  allowed_domains = ["musica.com"]
  start_urls = ('http://www.musica.com/letras.asp?letras=canciones',)

  def parse(self, response):
    for path in response.css('a::attr("href")').extract():
      #if re.match(r'letras.asp\?g=[A-Z1]$', path):
      if re.match(r'letras.asp\?g=[A]', path):
        yield scrapy.Request(response.urljoin(path), self.parse_artists_starting_with)

  def parse_artists_starting_with(self, response):
    for a in response.css('a'):
      if len(a.css('b')) == 2:
        path = a.css('a::attr(href)').extract_first()
        artist_name = a.css('b:nth-child(2)::text').extract_first()
        if artist_name == 'Akwid':
          yield {
            'type': 'artist',
            'path': path,
            'artist_name': artist_name,
          }
          yield scrapy.Request(response.urljoin(path), self.parse_songs_by_artist,
              meta={'artist_name': artist_name})

  def parse_songs_by_artist(self, response):
    artist_name = response.meta['artist_name']
    for a in response.css('a'):
      if a.css('b'):
        path = a.css('a::attr(href)').extract_first()
        long_song_name = a.css('a::text').extract_first()
        if long_song_name:
          match = re.match(r' Letras de ' + re.escape(artist_name) + ' - (.*)',
            long_song_name)
          if match:
            song_name = match.group(1)
            yield {
              'type': 'song',
              'path': path,
              'artist_name': artist_name,
              'song_name': song_name,
            }
            if True: # song_name == u'Jamás imaginé':
              yield scrapy.Request(response.urljoin(path), self.parse_song_text,
                  meta={
                    'artist_name': artist_name,
                    'song_name': song_name,
                    'path': path,
                  })

  def parse_song_text(self, response):
    artist_name = response.meta['artist_name']
    song_name   = response.meta['song_name']
    path        = response.meta['path']
    song_heading = response.css('h2 > font > b::text').extract_first()
    song_text    = response.css('p > font::text').extract()
    yield {
      'type':        'song_text',
      'path':        path,
      'artist_name': artist_name,
      'song_name':   song_name,
      'song_text':   song_text,
    }
