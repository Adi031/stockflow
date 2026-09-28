-- KEYS[1] = flash sale stock counter key, e.g. "flashsale:{id}:stock"
-- KEYS[2] = this user's purchased-quantity key, e.g. "flashsale:{id}:user:{userId}"
-- ARGV[1] = requested quantity
-- ARGV[2] = per-user limit
--
-- Runs as a single atomic operation inside Redis (Redis executes one Lua script to completion
-- with no other command interleaved), so this is the mechanism that makes "check stock, check
-- user limit, decrement, record" race-free even under thousands of concurrent requests hitting
-- the same key - no per-request round trip to Postgres, no lock contention.
--
-- Returns: 1 = success, 0 = sold out (not enough stock left), -1 = per-user limit reached

local stockKey = KEYS[1]
local userKey = KEYS[2]
local qty = tonumber(ARGV[1])
local perUserLimit = tonumber(ARGV[2])

local alreadyBought = tonumber(redis.call('GET', userKey) or '0')
if alreadyBought + qty > perUserLimit then
    return -1
end

local stock = tonumber(redis.call('GET', stockKey) or '0')
if stock < qty then
    return 0
end

redis.call('DECRBY', stockKey, qty)
redis.call('INCRBY', userKey, qty)
redis.call('EXPIRE', userKey, 86400)

return 1
