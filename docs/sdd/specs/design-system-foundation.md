# Design System Foundation

**Status:** Ready for implementation  
**Issue:** none  
**Product decision owner:** Isabella Camargo / QuiStock Team

## Problem and goal

The mobile application has inconsistent visual tokens across layouts: font sizes range from unreadable (8-10sp), navbar icon heights differ arbitrarily (25-35dp), corner radii are scattered without a standard (8dp, 14dp, 18dp, 20dp, 21dp), and screens are not adapted to tablet displays (such as the Samsung Galaxy Tab A7 Lite, 8.7", 1340x800).

The goal of this change is to establish the centralized Design System Foundation in `style/design-system-foundation`:
- Define official color palette tokens and dark theme support.
- Centralize dimensions (spacings, card insets, navbar icon sizes, corner radii, splash logo).
- Establish typography scale (accessible font sizes based on modern standards, Montserrat font family).
- Implement responsive dimensions for phone and tablet (`values/dimens.xml` and `values-sw600dp/dimens.xml`) avoiding screen duplication.
- Centralize shared button, card, and input styles in `themes.xml` and reusable drawables.

## Scope

**In scope:**
- Official color tokens in `res/values/colors.xml`:
  - Main palette: Branco (`#F8E9F9`), Lavana (`#AF36DF`), Limão (`#89B751`), Violeta (`#7F3AEF`), Petróleo (`#11061F`).
  - Additional tokens: Amarelo tag (`#F3F02E`), Rosa tag (`#DA4DC8`), Petrolhinho (`#210E3E`).
- Dark theme baseline in `res/values/themes.xml` and `res/values-night/themes.xml`.
- Standard dimensions in `res/values/dimens.xml` and tablet responsive dimensions in `res/values-sw600dp/dimens.xml`:
  - Corner radii: Violet buttons & inputs (17dp), cards (21.71dp / 22dp), alert card buttons (12dp).
  - Navbar: unified icon size (26dp phone, 32dp tablet) and bar height.
  - Typography scale: caption (12sp), body small (13sp), body/input (14sp), button (15sp), subtitle (16sp), title small (18sp), title (20sp), headline (26sp).
  - Button dimensions: max width token (398dp) and standard accessible height (50dp).
  - Card margins and inner paddings.
  - Splash logo dimensions.
- Downloadable/system Montserrat font family configuration in `res/font/montserrat.xml`.
- Updating shared drawables (`bg_login_button.xml`, `bg_input_outline.xml`, `bg_card_interference_category.xml`, `bg_alert.xml`, `bg_btn_alert.xml`, `bg_card_active.xml`) to reference central dimension and color tokens.

**Out of scope:**
- Rewriting individual feature screens (Login, Home/Interferences, Chatbot), except for the manager Home product-control icon requested for this branch.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | All required color tokens exist in `colors.xml` and match the hex codes provided. | Unit test / Resource verification |
| AC-02 | Corner radius tokens exist: 17dp for buttons/inputs, 21.71dp (22dp) for cards, 12dp for alert buttons. | Resource verification / Build check |
| AC-03 | Typography scale defines legible font sizes (minimum 12sp for secondary text, 14sp for body/inputs, 15sp for buttons). | Resource verification |
| AC-04 | Responsive tablet dimensions exist in `values-sw600dp/dimens.xml` (8.7" tablet scale). | Resource verification |
| AC-05 | Shared drawables reference centralized `@dimen` and `@color` attributes. | Compilation and `lintDebug` |
| AC-06 | All existing unit tests pass without regression. | `.\gradlew.bat testDebugUnitTest` |
| AC-07 | The manager Home bottom navigation uses the product-control icon in place of notifications and opens product control when selected. | Manual navigation check in the demo flow. |

## Technical impact

- Files modified/added:
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/values/dimens.xml`
  - `app/src/main/res/values-sw600dp/dimens.xml`
  - `app/src/main/res/values/themes.xml`
  - `app/src/main/res/values-night/themes.xml`
  - `app/src/main/res/font/montserrat.xml`
  - `app/src/main/res/drawable/bg_*.xml`
- Manager Home bottom navigation and demo route to product control.
- No database or backend migrations required.
