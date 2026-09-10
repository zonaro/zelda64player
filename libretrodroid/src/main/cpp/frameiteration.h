#ifndef LIBRETRODROID_FRAMEITERATION_H
#define LIBRETRODROID_FRAMEITERATION_H

#include <cstddef>

namespace libretrodroid {
/** Evaluates observers between core frames, including every accelerated frame. */
template <typename RunFrame, typename ObserveFrame>
void runObservedFrames(std::size_t count, RunFrame runFrame, ObserveFrame observeFrame) {
    for (std::size_t i = 0; i < count; ++i) {
        runFrame();
        if (!observeFrame()) return;
    }
}
}

#endif
