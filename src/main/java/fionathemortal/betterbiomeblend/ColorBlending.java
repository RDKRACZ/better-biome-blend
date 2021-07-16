package fionathemortal.betterbiomeblend;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.level.ColorResolver;

import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class ColorBlending
{
    public static final Lock                    freeBlendBuffersLock = new ReentrantLock();
    public static final Stack<ColorBlendBuffer> freeBlendBuffers     = new Stack<>();

    public static final byte[]
    neighbourOffsets =
    {
        -1, -1,
         0, -1,
         1, -1,
        -1,  0,
         0,  0,
         1,  0,
        -1,  1,
         0,  1,
         1,  1
    };

    public static final byte[]
    neighbourRectParams =
    {
        -1, -1,  0,  0, -16, -16,  0,  0,
         0, -1,  0,  0,   0, -16,  0,  0,
         0, -1, -1,  0,  16, -16,  0,  0,
        -1,  0,  0,  0, -16,   0,  0,  0,
         0,  0,  0,  0,   0,   0,  0,  0,
         0,  0, -1,  0,  16,   0,  0,  0,
        -1,  0,  0, -1, -16,  16,  0,  0,
         0,  0,  0, -1,   0,  16,  0,  0,
         0,  0, -1, -1,  16,  16,  0,  0
    };

    public static int
    getNeighbourOffsetX(int chunkIndex)
    {
        int result = neighbourOffsets[2 * chunkIndex + 0];

        return result;
    }

    public static int
    getNeighbourOffsetZ(int chunkIndex)
    {
        int result = neighbourOffsets[2 * chunkIndex + 1];

        return result;
    }

    public static int
    getNeighbourRectMinX(int chunkIndex, int radius)
    {
        int offset = 8 * chunkIndex;
        int result = neighbourRectParams[offset + 0] & (16 - radius);

        return result;
    }

    public static int
    getNeighbourRectMinZ(int chunkIndex, int radius)
    {
        int offset = 8 * chunkIndex;
        int result = neighbourRectParams[offset + 1] & (16 - radius);

        return result;
    }

    public static int
    getNeighbourRectMaxX(int chunkIndex, int radius)
    {
        int offset = 8 * chunkIndex;
        int result = (neighbourRectParams[offset + 2] & (radius - 16)) + 16;

        return result;
    }

    public static int
    getNeighbourRectMaxZ(int chunkIndex, int radius)
    {
        int offset = 8 * chunkIndex;
        int result = (neighbourRectParams[offset + 3] & (radius - 16)) + 16;

        return result;
    }

    public static int
    getNeighbourRectBlendCacheMinX(int chunkIndex, int radius)
    {
        int offset = 8 * chunkIndex;
        int result = Math.max(neighbourRectParams[offset + 4] + radius, 0);

        return result;
    }

    public static int
    getNeighbourRectBlendCacheMinZ(int chunkIndex, int radius)
    {
        int offset = 8 * chunkIndex;
        int result = Math.max(neighbourRectParams[offset + 5] + radius, 0);

        return result;
    }

    public static ColorBlendBuffer
    acquireBlendBuffer(int blendRadius)
    {
        ColorBlendBuffer result = null;

        freeBlendBuffersLock.lock();

        while (!freeBlendBuffers.empty())
        {
            ColorBlendBuffer buffer = freeBlendBuffers.pop();

            if (buffer.blendRadius == blendRadius)
            {
                result = buffer;
                break;
            }
        }

        freeBlendBuffersLock.unlock();

        if (result == null)
        {
            result = new ColorBlendBuffer(blendRadius);
        }

        return result;
    }

    public static void
    releaseBlendBuffer(ColorBlendBuffer cache)
    {
        freeBlendBuffersLock.lock();

        int blendRadius = BetterBiomeBlendClient.getBlendRadiusSetting();

        if (cache.blendRadius == blendRadius)
        {
            freeBlendBuffers.push(cache);
        }

        freeBlendBuffersLock.unlock();
    }

    public static void
    gatherRawColorsForChunk(
        World         world,
        byte[]        result,
        int           chunkX,
        int           chunkZ,
        ColorResolver colorResolver)
    {
        BlockPos.Mutable blockPos = new BlockPos.Mutable();

        int blockX = 16 * chunkX;
        int blockZ = 16 * chunkZ;

        int dstIndex = 0;

        double baseXF64 = (double)blockX;
        double baseZF64 = (double)blockZ;

        double zF64 = baseZF64;

        for (int z = 0;
            z < 16;
            ++z)
        {
            double xF64 = baseXF64;

            for (int x = 0;
                x < 16;
                ++x)
            {
                blockPos.set(blockX + x, 0, blockZ + z);

                int color = colorResolver.getColor(world.getBiome(blockPos), xF64, zF64);

                int colorR = Color.RGBAGetR(color);
                int colorG = Color.RGBAGetG(color);
                int colorB = Color.RGBAGetB(color);

                result[3 * dstIndex + 0] = (byte)colorR;
                result[3 * dstIndex + 1] = (byte)colorG;
                result[3 * dstIndex + 2] = (byte)colorB;

                ++dstIndex;

                xF64 += 1.0;
            }

            zF64 += 1.0;
        }
    }

    public static void
    gatherRawColorsForRect(
        World         world,
        ColorResolver colorResolver,
        int           chunkX,
        int           chunkZ,
        int           minX,
        int           maxX,
        int           minZ,
        int           maxZ,
        byte[]        result)
    {
        BlockPos.Mutable blockPos = new BlockPos.Mutable();

        int blockX = 16 * chunkX;
        int blockZ = 16 * chunkZ;

        double baseXF64 = (double)(blockX + minX);
        double baseZF64 = (double)(blockZ + minZ);

        double zF64 = baseZF64;

        for (int z = minZ;
            z < maxZ;
            ++z)
        {
            double xF64 = baseXF64;

            for (int x = minX;
                x < maxX;
                ++x)
            {
                int currentR = 0xFF & result[3 * (16 * z + x) + 0];
                int currentG = 0xFF & result[3 * (16 * z + x) + 1];
                int currentB = 0xFF & result[3 * (16 * z + x) + 2];

                int commonBits = currentR & currentG & currentB;

                if (commonBits == 0xFF)
                {
                    blockPos.set(blockX + x, 0, blockZ + z);

                    int color = colorResolver.getColor(world.getBiome(blockPos), xF64, zF64);

                    int colorR = Color.RGBAGetR(color);
                    int colorG = Color.RGBAGetG(color);
                    int colorB = Color.RGBAGetB(color);

                    result[3 * (16 * z + x) + 0] = (byte)colorR;
                    result[3 * (16 * z + x) + 1] = (byte)colorG;
                    result[3 * (16 * z + x) + 2] = (byte)colorB;
                }

                xF64 += 1.0;
            }

            zF64 += 1.0;
        }
    }

    public static void
    fillRectWithDefaultColor(
        World         world,
        ColorResolver colorResolver,
        int           chunkX,
        int           chunkZ,
        int           minX,
        int           maxX,
        int           minZ,
        int           maxZ,
        byte[]        result)
    {
        Biome plains = BuiltinRegistries.BIOME.get(BetterBiomeBlendClient.PLAINS);

        int color = 0;

        if (plains != null)
        {
            color = colorResolver.getColor(plains, 0, 0);
        }

        int colorR = Color.RGBAGetR(color);
        int colorG = Color.RGBAGetG(color);
        int colorB = Color.RGBAGetB(color);

        for (int z = minZ;
            z < maxZ;
            ++z)
        {
            for (int x = minX;
                x < maxX;
                ++x)
            {
                result[3 * (16 * z + x) + 0] = (byte)colorR;
                result[3 * (16 * z + x) + 1] = (byte)colorG;
                result[3 * (16 * z + x) + 2] = (byte)colorB;
            }
        }
    }

    public static void
    gatherRawColorsToCache(
        World         world,
        ColorResolver colorResolver,
        int           chunkX,
        int           chunkZ,
        int           blendRadius,
        byte[]        result,
        int           chunkIndex)
    {
        Chunk chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.BIOMES, false);

        int minX = getNeighbourRectMinX(chunkIndex, blendRadius);
        int minZ = getNeighbourRectMinZ(chunkIndex, blendRadius);
        int maxX = getNeighbourRectMaxX(chunkIndex, blendRadius);
        int maxZ = getNeighbourRectMaxZ(chunkIndex, blendRadius);

        if (chunk != null)
        {
            gatherRawColorsForRect(
                world,
                colorResolver,
                chunkX,
                chunkZ,
                minX,
                maxX,
                minZ,
                maxZ,
                result);
        }
        else
        {
            fillRectWithDefaultColor(
                world,
                colorResolver,
                chunkX,
                chunkZ,
                minX,
                maxX,
                minZ,
                maxZ,
                result);
        }
    }

    public static void
    gatherRawColorsToCaches(
        World           world,
        ColorResolver   colorResolver,
        int             colorType,
        int             chunkX,
        int             chunkZ,
        int             blendRadius,
        ColorCache      rawCache,
        byte[]          result)
    {
        for (int chunkIndex = 0;
            chunkIndex < 9;
            ++chunkIndex)
        {
            int offsetX = getNeighbourOffsetX(chunkIndex);
            int offsetZ = getNeighbourOffsetZ(chunkIndex);

            int rawChunkX = chunkX + offsetX;
            int rawChunkZ = chunkZ + offsetZ;

            ColorChunk rawChunk = rawCache.getOrDefaultInitializeChunk(rawChunkX, rawChunkZ, colorType);

            gatherRawColorsToCache(
                world,
                colorResolver,
                rawChunkX,
                rawChunkZ,
                blendRadius,
                rawChunk.data,
                chunkIndex);

            copyRawCacheToBlendCache(rawChunk.data, result, chunkIndex, blendRadius);

            rawCache.releaseChunk(rawChunk);
        }
    }

    public static void
    copyRawCacheToBlendCache(byte[] rawCache, byte[] result, int chunkIndex, int blendRadius)
    {
        int srcMinX = getNeighbourRectMinX(chunkIndex, blendRadius);
        int srcMinZ = getNeighbourRectMinZ(chunkIndex, blendRadius);
        int srcMaxX = getNeighbourRectMaxX(chunkIndex, blendRadius);
        int srcMaxZ = getNeighbourRectMaxZ(chunkIndex, blendRadius);
        int dstMinX = getNeighbourRectBlendCacheMinX(chunkIndex, blendRadius);
        int dstMinZ = getNeighbourRectBlendCacheMinZ(chunkIndex, blendRadius);

        int dstDim = 16 + 2 * blendRadius;

        int dstLine = 3 * (dstMinX + dstMinZ * dstDim);
        int srcLine = 3 * (srcMinX + srcMinZ * 16);

        for (int z = srcMinZ;
            z < srcMaxZ;
            ++z)
        {
            int dstIndex = dstLine;
            int srcIndex = srcLine;

            for (int x = srcMinX;
                x < srcMaxX;
                ++x)
            {
                result[dstIndex + 0] = rawCache[srcIndex + 0];
                result[dstIndex + 1] = rawCache[srcIndex + 1];
                result[dstIndex + 2] = rawCache[srcIndex + 2];

                dstIndex += 3;
                srcIndex += 3;
            }

            dstLine += 3 * dstDim;
            srcLine += 3 * 16;
        }
    }

    public static void
    blendColorsForChunk(World world, byte[] result, ColorBlendBuffer blendCache)
    {
        float[] R = blendCache.R;
        float[] G = blendCache.G;
        float[] B = blendCache.B;

        int blendRadius = blendCache.blendRadius;
        int blendDim = 2 * blendRadius + 1;
        int blendCacheDim = 16 + 2 * blendRadius;
        int blendCount = blendDim * blendDim;

        for (int x = 0;
            x < blendCacheDim;
            ++x)
        {
            R[x] = Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * x + 0]);
            G[x] = Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * x + 1]);
            B[x] = Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * x + 2]);
        }

        for (int z = 1;
            z < blendDim;
            ++z)
        {
            for (int x = 0;
                x < blendCacheDim;
                ++x)
            {
                R[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * (blendCacheDim * z + x) + 0]);
                G[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * (blendCacheDim * z + x) + 1]);
                B[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * (blendCacheDim * z + x) + 2]);
            }
        }

        for (int z = 0;
            z < 16;
            ++z)
        {
            float accumulatedR = 0;
            float accumulatedG = 0;
            float accumulatedB = 0;

            for (int x = 0;
                x < blendDim;
                ++x)
            {
                accumulatedR += R[x];
                accumulatedG += G[x];
                accumulatedB += B[x];
            }

            for (int x = 0;
                x < 16;
                ++x)
            {
                float colorR = accumulatedR / blendCount;
                float colorG = accumulatedG / blendCount;
                float colorB = accumulatedB / blendCount;

                result[3 * (16 * z + x) + 0] = Color.linearFloatTosRGBByte(colorR);
                result[3 * (16 * z + x) + 1] = Color.linearFloatTosRGBByte(colorG);
                result[3 * (16 * z + x) + 2] = Color.linearFloatTosRGBByte(colorB);

                if (x < 15)
                {
                    accumulatedR += R[x + blendDim] - R[x];
                    accumulatedG += G[x + blendDim] - G[x];
                    accumulatedB += B[x + blendDim] - B[x];
                }
            }

            if (z < 15)
            {
                for (int x = 0;
                    x < blendCacheDim;
                    ++x)
                {
                    int index1 = 3 * (blendCacheDim * (z           ) + x);
                    int index2 = 3 * (blendCacheDim * (z + blendDim) + x);

                    R[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index2 + 0]) - Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index1 + 0]);
                    G[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index2 + 1]) - Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index1 + 1]);
                    B[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index2 + 2]) - Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index1 + 2]);
                }
            }
        }
    }

    public static void
    generateBlendedColorChunk(
        World         world,
        ColorResolver colorResolverIn,
        int           colorType,
        int           chunkX,
        int           chunkZ,
        ColorCache    rawCache,
        byte[]        result)
    {
        int blendRadius = BetterBiomeBlendClient.getBlendRadiusSetting();

        if (blendRadius > 0 &&
            blendRadius <= BetterBiomeBlendClient.BIOME_BLEND_RADIUS_MAX)
        {
            ColorBlendBuffer blendBuffer = acquireBlendBuffer(blendRadius);

            gatherRawColorsToCaches(world, colorResolverIn, colorType, chunkX, chunkZ, blendBuffer.blendRadius, rawCache, blendBuffer.color);

            blendColorsForChunk(world, result, blendBuffer);

            releaseBlendBuffer(blendBuffer);
        }
        else
        {
            gatherRawColorsForChunk(world, result, chunkX, chunkZ, colorResolverIn);
        }
    }
}