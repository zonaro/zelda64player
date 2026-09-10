#include "../../main/cpp/frameiteration.h"
#include <cassert>
#include <vector>

int main() {
    int memory = 0;
    std::vector<int> observed;
    // A transient condition present only on frame 2 must survive fast-forward.
    libretrodroid::runObservedFrames(6, [&] { ++memory; }, [&] {
        observed.push_back(memory);
        return true;
    });
    assert((observed == std::vector<int>{1, 2, 3, 4, 5, 6}));
    libretrodroid::runObservedFrames(0, [&] { ++memory; }, [&] { assert(false); return true; });
    assert(memory == 6);
    libretrodroid::runObservedFrames(6, [&] { ++memory; }, [&] { return false; });
    assert(memory == 7); // A callback failure stops further core execution.
}
