import { useEffect, useState } from 'react';

export function useCountUp(target: number, duration = 1200, enabled = true) {
    const [value, setValue] = useState(0);

    useEffect(() => {
        if (!enabled || target <= 0) {
            setValue(target);
            return;
        }

        setValue(0);
        const start = performance.now();

        function tick(now: number) {
            const elapsed = now - start;
            const progress = Math.min(elapsed / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3); // easeOutCubic
            setValue(Math.round(eased * target));
            if (progress < 1) requestAnimationFrame(tick);
        }

        requestAnimationFrame(tick);
    }, [target, duration, enabled]);

    return value;
}
