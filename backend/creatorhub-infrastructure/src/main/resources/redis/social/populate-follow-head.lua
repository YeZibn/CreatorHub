local ttl = tonumber(ARGV[1])
local entries = (#ARGV - 1) / 2
if not ttl or ttl % 1 ~= 0 or ttl <= 0 or entries < 1 or entries > 51 or entries % 1 ~= 0 then
    return redis.error_reply('Invalid follow cache payload')
end

local members = {}
for i = 2, #ARGV, 2 do
    local score = tonumber(ARGV[i])
    local member = ARGV[i + 1]
    local empty = entries == 1 and score == 0 and member == '__empty__'
    if not empty then
        if not score or score % 1 ~= 0 or math.abs(score) > 9007199254740991
                or #member ~= 19 or not string.match(member, '^%d+$')
                or member == '0000000000000000000' or member > '9223372036854775807'
                or members[member] then
            return redis.error_reply('Invalid follow cache entry')
        end
        members[member] = true
    end
end

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end
redis.call('ZADD', KEYS[1], unpack(ARGV, 2))
redis.call('PEXPIRE', KEYS[1], ttl)
return 1
