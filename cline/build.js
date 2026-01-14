import { build } from 'esbuild';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);

async function buildProject() {
  try {
    console.log('Building cline-exec bundle...');
    
    await build({
      entryPoints: ['src/cline-exec.ts'],
      bundle: true,
      outfile: 'dist/cline-exec.js',
      platform: 'node',
      format: 'esm',
      target: 'node22',
      external: [
        '@bufbuild/protobuf',
        '@grpc/grpc-js',
        'grpc-health-check'
      ],
      sourcemap: false,
      minify: false,
      treeShaking: true,
      loader: {
        '.ts': 'ts'
      },
      resolveExtensions: ['.ts', '.js'],
      logLevel: 'info'
    });
    
    console.log('✅ Bundle built successfully!');
    console.log('📦 Output: dist/cline-exec.js');
    
  } catch (error) {
    console.error('❌ Build failed:', error);
    process.exit(1);
  }
}

// Run build if this file is executed directly
if (import.meta.url === `file://${__filename}`) {
  buildProject();
}
