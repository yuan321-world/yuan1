local v=redis.call('HMGET',KEYS[1],'userId','sv','lastSeen','absoluteExpiresAt','idleSeconds','state')
local now=tonumber(ARGV[3])
if not v[1] or v[1]~=ARGV[1] or v[2]~=ARGV[2] or v[6]~='ACTIVE' or now>=tonumber(v[4]) or now<tonumber(v[3]) or now-tonumber(v[3])>=tonumber(v[5]) then return 0 end
if ARGV[4]=='1' then redis.call('HSET',KEYS[1],'lastSeen',ARGV[3]) end
return 1
