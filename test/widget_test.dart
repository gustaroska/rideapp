import 'package:flutter_test/flutter_test.dart';

import 'package:rideapp/main.dart';

void main() {
  testWidgets('Hello world text is displayed', (WidgetTester tester) async {
    await tester.pumpWidget(const RideApp());

    expect(find.text('Hello, world! 👋'), findsOneWidget);
  });
}
