local current=redis.call('HGET',KEYS[1],'currentRefreshHash')
if not current then return 0 end
if ARGV[1]=='bearer' or current==ARGV[2] or redis.call('EXISTS',KEYS[2])==1 then
  redis.call('HSET',KEYS[1],'state','REVOKED');return 1
end
return 0
