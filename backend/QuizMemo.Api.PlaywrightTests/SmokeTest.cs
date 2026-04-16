using Microsoft.Playwright;

namespace QuizMemo.Api.PlaywrightTests;

public class SmokeTest
{
    [Fact]
    public async Task Chromium_can_launch_and_render_a_data_url()
    {
        using var playwright = await Playwright.CreateAsync();
        await using var browser = await playwright.Chromium.LaunchAsync(new()
        {
            Headless = true,
        });
        var page = await browser.NewPageAsync();

        await page.SetContentAsync("<html><head><title>QuizMemo</title></head><body><h1 id='hello'>hello</h1></body></html>");

        Assert.Equal("QuizMemo", await page.TitleAsync());
        Assert.Equal("hello", await page.Locator("#hello").InnerTextAsync());
    }
}
