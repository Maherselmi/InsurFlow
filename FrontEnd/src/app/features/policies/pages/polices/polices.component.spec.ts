import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PolicesComponent } from './polices.component';

describe('PolicesComponent', () => {
  let component: PolicesComponent;
  let fixture: ComponentFixture<PolicesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PolicesComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(PolicesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
