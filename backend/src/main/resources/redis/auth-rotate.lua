local v=redis.call('HMGET',KEYS[1],'userId','sv','lastSeen','absoluteExpiresAt','idleSeconds','state','currentRefreshHash')
local now=tonumber(ARGV[3])
if not v[1] or v[1]~=ARGV[1] or v[2]~=ARGV[2] or v[6]~='ACTIVE' or now>=tonumber(v[4]) or now<tonumber(v[3]) or now-tonumber(v[3])>=tonumber(v[5]) then return 0 end
if v[7]~=ARGV[4] then
  if redis.call('EXISTS',KEYS[2])==1 then redis.call('HSET',KEYS[1],'state','REVOKED');return -2 end
  return -1
end
redis.call('SET',KEYS[2],'1')
redis.call('EXPIREAT',KEYS[2],v[4])
redis.call('HSET',KEYS[1],'currentRefreshHash',ARGV[5],'lastSeen',ARGV[3])
return 1
