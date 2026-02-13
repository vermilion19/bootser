export interface CreatureAnimationMeta {
  name: string;
  frame_size: { w: number; h: number };
  animations: {
    idle?: AnimationClip;
    blink?: AnimationClip;
    sleep?: AnimationClip;
  };
}

interface AnimationClip {
  sheet: string;
  frames: number[];
  frame_duration_ms: number;
}

export interface CreatureTemplate {
  key: string;
  label: string;
  species: string;
  metaPath: string;
  previewGifPath?: string;
}

export interface LoadedCreatureAsset {
  template: CreatureTemplate;
  meta: CreatureAnimationMeta;
}

const DEFAULT_META_PATHS = ["/meta/juvenile_creature_animations_meta.json"];

const FALLBACK_TEMPLATES: CreatureTemplate[] = [
  {
    key: "juvenile",
    label: "Juvenile",
    species: "juvenile",
    metaPath: "/meta/juvenile_creature_animations_meta.json",
    previewGifPath: "/previews/juvenile_idle_preview.gif",
  },
];

function deriveTemplate(metaPath: string, meta: CreatureAnimationMeta): CreatureTemplate {
  const baseName = meta.name.split("_creature")[0] || meta.name;
  const key = baseName.toLowerCase();
  return {
    key,
    label: baseName.charAt(0).toUpperCase() + baseName.slice(1),
    species: key,
    metaPath,
    previewGifPath: `/previews/${key}_idle_preview.gif`,
  };
}

async function loadManifestMetaPaths(): Promise<string[]> {
  try {
    const res = await fetch("/meta/manifest.json");
    if (!res.ok) {
      return DEFAULT_META_PATHS;
    }
    const data = (await res.json()) as { files?: string[] };
    const files = data.files?.filter((file) => file.endsWith(".json")) ?? [];
    if (files.length === 0) {
      return DEFAULT_META_PATHS;
    }
    return files.map((file) => (file.startsWith("/") ? file : `/meta/${file}`));
  } catch {
    return DEFAULT_META_PATHS;
  }
}

export async function loadCreatureAssets(): Promise<LoadedCreatureAsset[]> {
  const metaPaths = await loadManifestMetaPaths();
  const assets = await Promise.all(
    metaPaths.map(async (metaPath) => {
      const res = await fetch(metaPath);
      if (!res.ok) {
        return null;
      }
      const meta = (await res.json()) as CreatureAnimationMeta;
      return {
        template: deriveTemplate(metaPath, meta),
        meta,
      } satisfies LoadedCreatureAsset;
    })
  );

  const valid = assets.filter((asset): asset is LoadedCreatureAsset => asset != null);
  if (valid.length > 0) {
    return valid;
  }

  return Promise.all(
    FALLBACK_TEMPLATES.map(async (template) => {
      const metaRes = await fetch(template.metaPath);
      if (!metaRes.ok) {
        throw new Error("No creature meta found in /public/meta");
      }
      return {
        template,
        meta: (await metaRes.json()) as CreatureAnimationMeta,
      } satisfies LoadedCreatureAsset;
    })
  );
}
