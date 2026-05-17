import aiomysql, asyncio

async def test():
    conn = await aiomysql.connect(host='127.0.0.1', port=13306, user='root', password='orange_logistics_2024', db='orange_logistics')
    cur = await conn.cursor()
    await cur.execute('SELECT 1')
    print(await cur.fetchone())
    conn.close()

asyncio.run(test())
