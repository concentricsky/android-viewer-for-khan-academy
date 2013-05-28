import logging
import os
import sqlite3
import json
import re
from restful_lib import Connection

fields = {
    'android_metadata': ('locale'),
    'enqueueddownload': ('youtubeId',),
    'topic': ('_id','child_kind','video_count','downloaded_video_count','standalone_title','title','description','ka_url','hide','parentTopic_id','ancestry','seq','kind','thumb_id'),
    'user': ('user_id','joined','nickname','token','prettified_user_email','secret','points','total_seconds_watched','isSignedIn','kind',),
    'uservideo': ('id','user_id','video_id','completed','duration','last_second_watched','last_watched','points','seconds_watched','kind'),
    'video': ('readable_id','download_status','keywords','progress_key','duration','youtube_id','mp4url','pngurl','m3u8url','date_added','views','title','description','ka_url','hide','parentTopic_id','ancestry','seq','kind','dlm_id'),
    # 'video_youtube_id_idx''video':('youtube_id'),
}

# return (youtube id of thumbnail, descendant video count)
def parse_topic(topic, ancestry, topics, videos, topicvideos):
    kind = topic.get('kind', None)
    if 'Topic' == kind:
        topics.append(topic)
        ancestry = '|'.join((ancestry, topic.get('id')))
        seq = 0
        thumb_id = None
        child_kind = None
        video_count = 0
        for child in topic.get('children'):
            if child.get('kind') in ['Topic', 'Video']:
                child_kind = child.get('kind')
                child['parentTopic_id'] = topic.get('id')
                child['seq'] = seq
                child['ancestry'] = ancestry
                seq += 1
                youtube_id, count = parse_topic(child, ancestry, topics, videos, topicvideos)
                video_count += count
                # keep the youtube id of the first child video for the topic thumbnail
                if youtube_id is not None and thumb_id is None:
                    thumb_id = youtube_id
        topic['thumb_id'] = thumb_id
        topic['child_kind'] = child_kind
        if topic.get('seq', None) is None:
            topic['seq'] = 0 # for root, where it isn't set by the parent
        topic['video_count'] = video_count
        topic['downloaded_video_count'] = 0
        return thumb_id, video_count
    elif 'Video' == kind:
        video = topic
        topicvideos.append({'topic_id': video['parentTopic_id'], 'video_id': video['readable_id']})
        #if not any(map(lambda v: v['readable_id'] == video['readable_id'], videos)):
        videos.append(video)
        urls = video.get('download_urls', None)
        if urls:
            video['mp4url'] = urls.get('mp4')
            video['pngurl'] = urls.get('png', None)
            video['m3u8url'] = urls.get('m3u8')
        return video.get('youtube_id', None), 1

def create_database(dbpath):
    db = sqlite3.connect(dbpath)
    cursor = db.cursor()

    schema = """
    PRAGMA legacy_file_format = true;
    CREATE TABLE android_metadata (locale TEXT);
    
    CREATE TABLE `topic` (`_id` VARCHAR , `child_kind` VARCHAR , `video_count` INTEGER NOT NULL DEFAULT 0, `downloaded_video_count` INTEGER NOT NULL DEFAULT 0, `standalone_title` VARCHAR , `title` VARCHAR , `description` VARCHAR , `ka_url` VARCHAR , `hide` VARCHAR , `parentTopic_id` VARCHAR , `ancestry` VARCHAR , `seq` INTEGER , `kind` VARCHAR , `thumb_id` VARCHAR , PRIMARY KEY (`_id`) );
    CREATE TABLE `video` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT , `readable_id` VARCHAR, `download_status` INTEGER NOT NULL DEFAULT 0, `keywords` VARCHAR , `progress_key` VARCHAR , `duration` INTEGER NOT NULL DEFAULT 0, `youtube_id` VARCHAR , `mp4url` VARCHAR , `pngurl` VARCHAR , `m3u8url` VARCHAR , `date_added` VARCHAR , `views` INTEGER NOT NULL DEFAULT 0, `title` VARCHAR , `description` VARCHAR , `ka_url` VARCHAR , `hide` VARCHAR , `parentTopic_id` VARCHAR , `ancestry` VARCHAR , `seq` INTEGER NOT NULL DEFAULT 0, `kind` VARCHAR, `dlm_id` INTEGER NOT NULL DEFAULT 0, UNIQUE (`readable_id`) ON CONFLICT IGNORE );
    CREATE TABLE `topicvideo` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `topic_id` VARCHAR, `video_id` VARCHAR, UNIQUE (`topic_id`, `video_id`) ON CONFLICT IGNORE );
    
    CREATE TABLE `enqueueddownload` (`youtubeId` VARCHAR , PRIMARY KEY (`youtubeId`) );
    CREATE TABLE `user` (`user_id` VARCHAR , `joined` VARCHAR , `nickname` VARCHAR , `token` VARCHAR , `prettified_user_email` VARCHAR , `secret` VARCHAR , `points` INTEGER NOT NULL DEFAULT 0, `total_seconds_watched` INTEGER NOT NULL DEFAULT 0, `isSignedIn` SMALLINT , `kind` VARCHAR , PRIMARY KEY (`nickname`) );
    CREATE TABLE `uservideo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `user_id` VARCHAR , `video_id` VARCHAR , `completed` SMALLINT , `duration` INTEGER NOT NULL DEFAULT 0, `last_second_watched` INTEGER NOT NULL DEFAULT 0, `last_watched` VARCHAR , `points` INTEGER NOT NULL DEFAULT 0, `seconds_watched` INTEGER NOT NULL DEFAULT 0, `kind` VARCHAR );
    CREATE TABLE `caption` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT , `youtube_id` VARCHAR, `start_time` INTEGER, `end_time` INTEGER, `time_string` VARCHAR, `sub_order` REAL, `text` VARCHAR );
    CREATE TABLE `thumbnail` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT , `youtube_id` VARCHAR, `q` INTEGER, `availability` INTEGER, `data` BLOB );

    CREATE INDEX IF NOT EXISTS `caption_youtube_id_idx` on `caption` ( `youtube_id` );
    CREATE INDEX IF NOT EXISTS `thumbnail_youtube_id_idx` on `thumbnail` ( `youtube_id` );
    CREATE INDEX IF NOT EXISTS `thumbnail_q_idx` on `thumbnail` ( `q` );
    """

    for line in schema.split('\n'):
        cursor.execute(line)

    cursor.close()
    return db

def insert_topics(db, topics):
    # INSERT INTO table (field, field, field) VALUES (value, value, value);
    insert_string = 'INSERT INTO topic (%s) VALUES (%s);'
    cursor = db.cursor()
    for topic in topics:
        insert_dict = {}
        for key in fields.get('topic'):
            val = topic.get(key, None) if key != '_id' else topic.get('id', None)
            if val is not None:
                insert_dict[key] = val
        # These two are in corresponding order. http://docs.python.org/2/library/stdtypes.html#dict.items
        keys = ','.join(insert_dict.keys()).replace('\bid\b', '_id')
        values = insert_dict.values()
        placeholder = ','.join(('?' for i in range(len(values))))

        # import pdb;pdb.set_trace()
        sql = insert_string % (keys, placeholder)
        cursor.execute(sql, values)
    cursor.close()

    cursor = db.cursor()
    cursor.execute('SELECT count() FROM topic')
    logging.debug('%d of %d inserted' % ( cursor.fetchone()[0], len(topics) ))
    cursor.close()

def insert_videos(db, videos):
    # INSERT INTO table (field, field, field) VALUES (value, value, value);
    insert_string = 'INSERT INTO video (%s) VALUES (%s);'
    cursor = db.cursor()
    for video in videos:
        insert_dict = {}
        for key in fields.get('video'):
            val = video.get(key, None)# if key != '_id' else video.get('readable_id', None)
            if val is not None:
                insert_dict[key] = val
        # These two are in corresponding order. http://docs.python.org/2/library/stdtypes.html#dict.items
        keys = ','.join(insert_dict.keys())
        values = insert_dict.values()
        #import pdb;pdb.set_trace()
        placeholder = ','.join(('?' for i in range(len(values))))

        sql = insert_string % (keys, placeholder)
        cursor.execute(sql, values)
    cursor.close()

    cursor = db.cursor()
    cursor.execute('SELECT count() FROM video')
    logging.debug('%d of %d inserted' % ( cursor.fetchone()[0], len(videos) ))
    cursor.close()
    
def insert_topicvideos(db, topicvideos):
    insert_string = 'INSERT INTO topicvideo (topic_id, video_id) VALUES (?, ?);'
    cursor = db.cursor()
    for tv in topicvideos:
        cursor.execute(insert_string, (tv['topic_id'], tv['video_id']))
    cursor.close()

    cursor = db.cursor()
    cursor.execute('SELECT count() FROM topicvideo')
    logging.debug('%d of %d inserted' % ( cursor.fetchone()[0], len(topicvideos) ))
    cursor.close()

def main():
    logging.basicConfig(level=logging.DEBUG)
    try:
        os.remove('out.sqlite3')
    except OSError as e:
        if e.errno != 2:
            raise

    db = create_database('out.sqlite3')

    #logging.info('requesting new topic tree...')
    #base_url = 'http://www.khanacademy.org/api/v1/'
    #conn = Connection(base_url)
    #response = conn.request_get('/topictree')
    #logging.info('parsing json response...')
    #tree = json.loads(response.get('body'))
    #logging.info('writing to file...')
    #with open('../topictree', 'w') as f:
    #    f.write(json.dumps(tree))

    logging.info('loading topic tree file...')
    with open('../topictree', 'r') as f:
        tree = json.loads(f.read())

    # stick videos in one list and topics in another for future batch insert
    topics = []
    videos = []
    topicvideos = []
    logging.info('parsing tree...')
    parse_topic(tree, '', topics, videos, topicvideos)

    logging.info('inserting topics...')
    insert_topics(db, topics)
    logging.info('inserting videos...')
    insert_videos(db, videos)
    logging.info('inserting topicvideos...')
    insert_topicvideos(db, topicvideos)

    db.commit()
    logging.info('done!')

if __name__ == '__main__':
    exit(main())
