if redis.call('EXISTS',KEYS[1]) == 1 then return 0 end
redis.call('HSET',KEYS[1], 'userId',ARGV[1], 'sv',ARGV[2], 'createdAt',ARGV[3], 'lastSeen',ARGV[3], 'absoluteExpiresAt',ARGV[4], 'idleSeconds',ARGV[5], 'remember',ARGV[6], 'state','ACTIVE','currentRefreshHash',ARGV[7])
redis.call('EXPIREAT',KEYS[1],ARGV[4])
return 1
